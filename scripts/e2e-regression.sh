#!/usr/bin/env bash
# 一期联调回归脚本（接口级）：资金、幂等、提成、业绩、买赠、权限、约束、冻结、关单回滚、对账。
# 依赖：运行中的 mall-api(8081) 与 mall-admin(8080)、mysql/redis 客户端、本地 Redis（读取 Mock 短信验证码）。
# 数据隔离：脚本自建「一级店 → 二级店 → 三级店」三店链路，不依赖、不污染已有数据。
# 用法：API_BASE=http://127.0.0.1:8081 ADMIN_BASE=http://127.0.0.1:8080 ./scripts/e2e-regression.sh
set -uo pipefail

API="${API_BASE:-http://127.0.0.1:8081}/api"
ADMIN="${ADMIN_BASE:-http://127.0.0.1:8080}/admin"
MYSQL_CMD="${MYSQL_CMD:-mysql -uroot -p123456 qidirect_mall}"
REDIS_CMD="${REDIS_CMD:-redis-cli}"

PASS=0
FAIL=0
JSON_TMP=$(mktemp)
trap 'rm -f "$JSON_TMP"' EXIT

ok() { PASS=$((PASS + 1)); echo "  [PASS] $1"; }
bad() { FAIL=$((FAIL + 1)); echo "  [FAIL] $1  => $2"; }

# assert_eq 描述 期望 实际
assert_eq() {
  if [ "$2" = "$3" ]; then ok "$1"; else bad "$1" "预期 [$2] 实际 [$3]"; fi
}

assert_contains() {
  case "$2" in
    *"$3"*) ok "$1" ;;
    *) bad "$1" "不含 [$3]，实际 [$2]" ;;
  esac
}

# json_get json path：取嵌套字段（如 data.token、code、payNo），字符串/数字均输出裸值；取不到输出空
# 纯 grep/sed 实现，避免依赖外部解释器
json_get() {
  local path="$2"
  local key="${path##*.}"
  local flat
  flat=$(printf '%s' "$1" | tr -d '\n\r')
  local v
  v=$(printf '%s' "$flat" | grep -o "\"$key\":\"[^\"]*\"" | head -n1 | sed 's/^[^:]*:"//; s/"$//')
  if [ -z "$v" ]; then
    v=$(printf '%s' "$flat" | grep -o "\"$key\":[0-9.-]*" | head -n1 | sed 's/^[^:]*://')
  fi
  if [ -z "$v" ]; then
    v=$(printf '%s' "$flat" | grep -o "\"$key\":null" | head -n1 | sed 's/^[^:]*://')
    [ "$v" = "null" ] && v=""
  fi
  printf '%s' "$v"
}
json_num() { json_get "$1" "$2"; }

sql() { $MYSQL_CMD -N -B -e "$1" 2>/dev/null | tr -d '\r'; }

sms_code() { $REDIS_CMD get "sms:code:$1"; }
send_sms() {
  curl -s -X POST "$API/auth/sms" -H 'Content-Type: application/json' -d "{\"phone\":\"$1\"}" >/dev/null
}
# register 手机号 昵称 [邀请码] -> 注册响应
register() {
  send_sms "$1"
  local invite="${3:-}"
  curl -s -X POST "$API/auth/register" -H 'Content-Type: application/json' \
    -d "{\"phone\":\"$1\",\"code\":\"$(sms_code "$1")\",\"inviteCode\":\"$invite\",\"nickname\":\"$2\"}"
}
# store_auth token 姓名 身份证
store_auth() {
  curl -s -X POST "$API/store/auth" -H "Authorization: Bearer $1" -H 'Content-Type: application/json' \
    -d "{\"realName\":\"$2\",\"idCard\":\"$3\",\"storeName\":\"E2E-$2\",\"licenseNo\":\"\",\"bizImages\":[]}"
}
mock_pay() { json_get "$(curl -s -X GET "$API/notify/mock/pay/$1")" code; }
# mock_notify payNo amount：模拟扫呗成功回调（Mock 网关验签恒通过），用于验证关单后回调路径
mock_notify() {
  curl -s -X POST "$API/notify/saobei" -H 'Content-Type: application/x-www-form-urlencoded' \
    -d "terminal_trace=$1&return_code=01&result_code=01&total_fee=$2&out_trade_no=$1"
}
deposit_of() { sql "SELECT deposit_balance FROM wallet WHERE store_id=$1"; }
commission_of() { sql "SELECT commission_balance FROM wallet WHERE store_id=$1"; }

echo "== 0. 管理员登录 =="
ADMIN_TOKEN=$(json_get "$(curl -s -X POST "$ADMIN/auth/login" -H 'Content-Type: application/json' \
  -d '{"account":"admin","password":"admin123"}')" data.token)
[ -n "$ADMIN_TOKEN" ] && ok "管理员登录" || { bad "管理员登录" "无 token"; exit 1; }
AH="Authorization: Bearer $ADMIN_TOKEN"

echo "== 1. 注册 / 邀请绑定 / 实名 / 短信限流 =="
SUFFIX=$(( $(date +%s) % 100000 ))
A_PHONE="138$(printf '%08d' "$SUFFIX")"
B_PHONE="138$(printf '%08d' $((SUFFIX + 1)))"
C_PHONE="138$(printf '%08d' $((SUFFIX + 2)))"

A_TOKEN=$(json_get "$(register "$A_PHONE" "一级店")" data.token)
[ -n "$A_TOKEN" ] && ok "一级店注册" || { bad "一级店注册" "无 token，手机号 $A_PHONE"; exit 1; }
AH_S="Authorization: Bearer $A_TOKEN"

LIMIT=$(curl -s -X POST "$API/auth/sms" -H 'Content-Type: application/json' -d "{\"phone\":\"$A_PHONE\"}")
assert_contains "同号 60 秒限流" "$LIMIT" "10007"

NOAUTH=$(curl -s -X POST "$API/orders" -H "$AH_S" -H 'Content-Type: application/json' \
  -d '{"cartItemIds":[1],"receiverName":"张三","receiverPhone":"13900000000","receiverAddr":"杭州"}')
assert_contains "未实名不能下单" "$NOAUTH" "20003"

store_auth "$A_TOKEN" "张三" "330102199001011234" >/dev/null
assert_eq "实名提交后 auth_status" "1" "$(sql "SELECT auth_status FROM store WHERE phone='$A_PHONE'")"
A_ID=$(sql "SELECT id FROM store WHERE phone='$A_PHONE'")
A_INVITE=$(sql "SELECT invite_code FROM store WHERE phone='$A_PHONE'")
[ -n "$A_INVITE" ] && ok "生成邀请码（${A_INVITE}）" || bad "生成邀请码" "为空"

B_TOKEN=$(json_get "$(register "$B_PHONE" "二级店" "$A_INVITE")" data.token)
B_ID=$(sql "SELECT id FROM store WHERE phone='$B_PHONE'")
assert_eq "扫码注册绑定上级" "$A_ID" "$(sql "SELECT parent_id FROM store WHERE id=$B_ID")"
assert_eq "path 延续上级前缀" "/$A_ID/$B_ID/" "$(sql "SELECT path FROM store WHERE id=$B_ID")"
store_auth "$B_TOKEN" "李四" "330102199002022349" >/dev/null
assert_eq "二级店实名状态" "1" "$(sql "SELECT auth_status FROM store WHERE id=$B_ID")"

echo "== 2. 未加盟限制 =="
NOJOIN_QR=$(curl -s "$API/store/invite-qr" -H "Authorization: Bearer $B_TOKEN")
assert_contains "未加盟不能生成邀请二维码" "$NOJOIN_QR" "20004"

echo "== 3. 统一购买：一级店加盟 1 万档 =="
JOIN=$(curl -s -X POST "$API/purchase/orders" -H "$AH_S" -H 'Content-Type: application/json' -d '{"levelId":1,"agreed":true}')
JOIN_PAYNO=$(json_get "$JOIN" payNo)
[ -n "$JOIN_PAYNO" ] && ok "加盟单创建（$(json_get "$JOIN" orderNo)）" || bad "加盟单创建" "$JOIN"
assert_eq "加盟单金额 = 档位费用" "1000000" "$(json_num "$JOIN" amount)"

echo "== 4. 回调幂等：重复 Mock 回调 3 次只入账 1 次 =="
for _ in 1 2 3; do mock_pay "$JOIN_PAYNO" >/dev/null; done
assert_eq "加盟费全额转预存且幂等" "1000000" "$(deposit_of "$A_ID")"
assert_eq "JOIN 预存流水唯一" "1" "$(sql "SELECT COUNT(*) FROM wallet_log WHERE store_id=$A_ID AND biz_type='JOIN'")"
assert_eq "加盟后档位=1 万档" "1" "$(sql "SELECT level_id FROM store WHERE id=$A_ID")"
assert_eq "已付对价置值" "1000000" "$(sql "SELECT level_paid_amount FROM store WHERE id=$A_ID")"

echo "== 5. 未加盟可浏览商品 =="
BROWSE=$(curl -s "$API/products" -H "Authorization: Bearer $B_TOKEN")
assert_contains "未加盟可浏览商品" "$BROWSE" '"code":0'

echo "== 6. 窗口期补差：1 万档升 3 万档 =="
UPGRADE=$(curl -s -X POST "$API/purchase/orders" -H "$AH_S" -H 'Content-Type: application/json' -d '{"levelId":2,"agreed":true}')
assert_eq "补差金额 = 30000 - 10000" "2000000" "$(json_num "$UPGRADE" amount)"
mock_pay "$(json_get "$UPGRADE" payNo)" >/dev/null
assert_eq "升级后档位=3 万档" "2" "$(sql "SELECT level_id FROM store WHERE id=$A_ID")"
assert_eq "已付对价置值不累加" "3000000" "$(sql "SELECT level_paid_amount FROM store WHERE id=$A_ID")"

echo "== 7. 提成与业绩：二级店首次加盟，一级店拿 10% =="
B_JOIN=$(curl -s -X POST "$API/purchase/orders" -H "Authorization: Bearer $B_TOKEN" -H 'Content-Type: application/json' -d '{"levelId":1,"agreed":true}')
B_JOIN_PAYNO=$(json_get "$B_JOIN" payNo)
assert_eq "加盟单创建即回调成功" "0" "$(mock_pay "$B_JOIN_PAYNO")"
assert_eq "二级店档位=1 万档" "1" "$(sql "SELECT level_id FROM store WHERE id=$B_ID")"
assert_eq "一级提成记录 100000 分" "100000" \
  "$(sql "SELECT COALESCE(SUM(amount),0) FROM commission_record WHERE to_store_id=$A_ID AND biz_type='JOIN'")"
assert_eq "提成入佣金钱包" "100000" "$(commission_of "$A_ID")"
assert_eq "加盟业绩归属二级店" "1000000" \
  "$(sql "SELECT COALESCE(SUM(amount),0) FROM performance_record WHERE store_id=$B_ID AND biz_type='JOIN'")"

echo "== 8. 下单：3 万档买 12 瓶（仅 5 万档有买赠） =="
curl -s -X DELETE "$API/cart" -H "$AH_S" >/dev/null
CART=$(curl -s -X POST "$API/cart/items" -H "$AH_S" -H 'Content-Type: application/json' -d '{"skuId":1,"qty":12}')
CART_ID=$(json_num "$CART" id)
[ -n "$CART_ID" ] && ok "加入购物车" || bad "加入购物车" "$CART"
ORDER=$(curl -s -X POST "$API/orders" -H "$AH_S" -H 'Content-Type: application/json' \
  -d "{\"cartItemIds\":[$CART_ID],\"receiverName\":\"张三\",\"receiverPhone\":\"$A_PHONE\",\"receiverAddr\":\"杭州市西湖区测试路1号\"}")
ORDER_ID=$(json_num "$ORDER" id)
ORDER_PAYNO=$(json_get "$ORDER" payNo)
ORDER_TOTAL=$(json_num "$ORDER" totalAmount)
ORDER_DEPOSIT=$(json_num "$ORDER" depositAmount)
assert_eq "12 瓶 × 9500 分 = 114000" "114000" "$ORDER_TOTAL"
assert_eq "3 万档不下赠品" "0" \
  "$(sql "SELECT COALESCE(SUM(qty),0) FROM order_item WHERE order_id=$ORDER_ID AND is_gift=1")"

echo "== 9. 预存抵扣 + 在线支付，订单进入待发货 =="
mock_pay "$ORDER_PAYNO" >/dev/null
ONLINE=$(sql "SELECT online_amount FROM orders WHERE id=$ORDER_ID")
assert_eq "在线支付额 = 总额 - 预存抵扣" "$((ORDER_TOTAL - ORDER_DEPOSIT))" "$ONLINE"
assert_eq "订单进入待发货" "1" "$(sql "SELECT status FROM orders WHERE id=$ORDER_ID")"

echo "== 10. 发货 → 确认收货 =="
SHIP=$(curl -s -X POST "$ADMIN/orders/$ORDER_ID/ship" -H "$AH" -H 'Content-Type: application/json' \
  -d '{"shipType":1,"shipCompany":"顺丰","shipNo":"SF-E2E-001","shipImages":[]}')
assert_contains "后台发货" "$SHIP" '"code":0'
assert_eq "发货后订单状态=2" "2" "$(sql "SELECT status FROM orders WHERE id=$ORDER_ID")"
CONFIRM=$(curl -s -X POST "$API/orders/$ORDER_ID/confirm" -H "$AH_S")
assert_contains "确认收货" "$CONFIRM" '"code":0'
assert_eq "完成后订单状态=3" "3" "$(sql "SELECT status FROM orders WHERE id=$ORDER_ID")"

echo "== 11. 关单回滚：取消待支付订单预存全额退回 =="
DEPOSIT_BEFORE=$(deposit_of "$A_ID")
curl -s -X POST "$API/cart/items" -H "$AH_S" -H 'Content-Type: application/json' -d '{"skuId":1,"qty":1}' >/dev/null
CART2_ID=$(sql "SELECT id FROM cart_item WHERE store_id=$A_ID ORDER BY id DESC LIMIT 1")
ORDER2=$(curl -s -X POST "$API/orders" -H "$AH_S" -H 'Content-Type: application/json' \
  -d "{\"cartItemIds\":[$CART2_ID],\"useDeposit\":false,\"receiverName\":\"张三\",\"receiverPhone\":\"$A_PHONE\",\"receiverAddr\":\"杭州市西湖区测试路1号\"}")
ORDER2_ID=$(json_num "$ORDER2" id)
ORDER2_PAYNO=$(json_get "$ORDER2" payNo)
assert_contains "待支付订单含在线差额" "$ORDER2" '"code":0'
CANCEL=$(curl -s -X POST "$API/orders/$ORDER2_ID/cancel" -H "$AH_S")
assert_contains "取消待支付订单" "$CANCEL" '"code":0'
assert_eq "取消后预存全额退回" "$DEPOSIT_BEFORE" "$(deposit_of "$A_ID")"
assert_eq "订单状态为已取消" "4" "$(sql "SELECT status FROM orders WHERE id=$ORDER2_ID")"
assert_eq "支付单关闭" "4" "$(sql "SELECT status FROM payment WHERE pay_no='$ORDER2_PAYNO'")"

echo "== 12. 提现：窗口拦截 / 冻结 / 驳回不写流水 =="
# 临时把开放日配置为「今天」，确保可验证冻结与驳回；用例结束恢复原配置
WITHDRAW_DAYS_OLD=$(sql "SELECT cfg_value FROM sys_config WHERE cfg_key='withdraw_days'")
sql "UPDATE sys_config SET cfg_value='$(date +%-d)' WHERE cfg_key='withdraw_days'" >/dev/null
curl -s -X PUT "$API/store/bank" -H "$AH_S" -H 'Content-Type: application/json' \
  -d '{"bankHolder":"张三","bankName":"招商银行","bankAccount":"6225880212345678"}' >/dev/null
COMM_BEFORE=$(commission_of "$A_ID")
APPLY=$(curl -s -X POST "$API/withdraws" -H "$AH_S" -H 'Content-Type: application/json' -d '{"amount":50000}')
WITH_ID=$(json_num "$APPLY" id)
if [ -n "$WITH_ID" ]; then
  ok "提现申请创建（开放日含今日）"
  assert_eq "申请即冻结佣金" "50000" "$(sql "SELECT frozen_amount FROM wallet WHERE store_id=$A_ID")"
  LOGS_BEFORE=$(sql "SELECT COUNT(*) FROM wallet_log WHERE store_id=$A_ID")
  curl -s -X POST "$ADMIN/withdraws/$WITH_ID/reject" -H "$AH" -H 'Content-Type: application/json' -d '{"reason":"E2E 驳回"}' >/dev/null
  assert_eq "驳回不写余额流水" "$LOGS_BEFORE" "$(sql "SELECT COUNT(*) FROM wallet_log WHERE store_id=$A_ID")"
  assert_eq "驳回解冻" "0" "$(sql "SELECT frozen_amount FROM wallet WHERE store_id=$A_ID")"
  assert_eq "驳回后佣金不变" "$COMM_BEFORE" "$(commission_of "$A_ID")"
else
  bad "提现申请创建" "$APPLY"
fi
# 非开放日：把开放日改为不可能为今天的 32 表示空集合，验证窗口拦截
sql "UPDATE sys_config SET cfg_value='32' WHERE cfg_key='withdraw_days'" >/dev/null
CLOSED=$(curl -s -X POST "$API/withdraws" -H "$AH_S" -H 'Content-Type: application/json' -d '{"amount":50000}')
assert_contains "非开放日提现被拦截" "$CLOSED" "40003"
sql "UPDATE sys_config SET cfg_value='$WITHDRAW_DAYS_OLD' WHERE cfg_key='withdraw_days'" >/dev/null

echo "== 13. 升级金额：已过窗口期后按全额收款（口径：走预存单，档位与已缴对价不变） =="
sql "UPDATE store SET upgrade_deadline = DATE_SUB(NOW(), INTERVAL 1 DAY) WHERE id=$A_ID" >/dev/null
LEVEL_BEFORE=$(sql "SELECT level_id FROM store WHERE id=$A_ID")
PAID_BEFORE=$(sql "SELECT level_paid_amount FROM store WHERE id=$A_ID")
DEPOSIT_BEFORE13=$(deposit_of "$A_ID")
FULL=$(curl -s -X POST "$API/purchase/orders" -H "$AH_S" -H 'Content-Type: application/json' -d '{"levelId":3,"agreed":true}')
assert_eq "超窗口期 5 万档全额 5000000" "5000000" "$(json_num "$FULL" amount)"
assert_contains "超窗口期生成预存单而非补差单" "$FULL" '"bizType":"RECHARGE"'
mock_pay "$(json_get "$FULL" payNo)" >/dev/null
assert_eq "超期升档不改档位" "$LEVEL_BEFORE" "$(sql "SELECT level_id FROM store WHERE id=$A_ID")"
assert_eq "超期升档不改已缴对价" "$PAID_BEFORE" "$(sql "SELECT level_paid_amount FROM store WHERE id=$A_ID")"
assert_eq "超期升档全额入预存" "$((DEPOSIT_BEFORE13 + 5000000))" "$(deposit_of "$A_ID")"
# 窗口期内补齐至 5 万档（口径：1 万档在窗口期升 5 万只收差额，支付后档位与已缴对价置为 5 万档费）
sql "UPDATE store SET upgrade_deadline = DATE_ADD(NOW(), INTERVAL 30 DAY) WHERE id=$A_ID" >/dev/null
DIFF=$(curl -s -X POST "$API/purchase/orders" -H "$AH_S" -H 'Content-Type: application/json' -d '{"levelId":3,"agreed":true}')
assert_eq "窗口期内 1 万档升 5 万只收差额" "$((5000000 - PAID_BEFORE))" "$(json_num "$DIFF" amount)"
mock_pay "$(json_get "$DIFF" payNo)" >/dev/null
assert_eq "升级后档位=5 万档" "3" "$(sql "SELECT level_id FROM store WHERE id=$A_ID")"
assert_eq "升级后已缴对价=5 万档费" "5000000" "$(sql "SELECT level_paid_amount FROM store WHERE id=$A_ID")"
# 5 万档门店买 3 万档：按全额预存，档位与已缴对价不变
LOWER=$(curl -s -X POST "$API/purchase/orders" -H "$AH_S" -H 'Content-Type: application/json' -d '{"levelId":2,"agreed":true}')
assert_eq "5 万档买 3 万档按全额预存 3000000" "3000000" "$(json_num "$LOWER" amount)"
mock_pay "$(json_get "$LOWER" payNo)" >/dev/null
assert_eq "降档购买后档位仍为 5 万档" "3" "$(sql "SELECT level_id FROM store WHERE id=$A_ID")"
assert_eq "降档购买后已缴对价不变" "5000000" "$(sql "SELECT level_paid_amount FROM store WHERE id=$A_ID")"

echo "== 14. 买赠与订货提成：5 万档买 12 瓶送 3 瓶 =="
curl -s -X DELETE "$API/cart" -H "$AH_S" >/dev/null
CART3=$(curl -s -X POST "$API/cart/items" -H "$AH_S" -H 'Content-Type: application/json' -d '{"skuId":1,"qty":12}')
CART3_ID=$(json_num "$CART3" id)
DEPOSIT_BEFORE3=$(deposit_of "$A_ID")
ORDER3=$(curl -s -X POST "$API/orders" -H "$AH_S" -H 'Content-Type: application/json' \
  -d "{\"cartItemIds\":[$CART3_ID],\"receiverName\":\"张三\",\"receiverPhone\":\"$A_PHONE\",\"receiverAddr\":\"杭州市西湖区测试路1号\"}")
ORDER3_ID=$(json_num "$ORDER3" id)
ORDER3_TOTAL=$(json_num "$ORDER3" totalAmount)
ORDER3_DEPOSIT=$(json_num "$ORDER3" depositAmount)
ORDER3_ONLINE=$(json_num "$ORDER3" onlineAmount)
assert_eq "5 万档 12 瓶 × 9000 = 108000" "108000" "$ORDER3_TOTAL"
assert_eq "满 12 瓶送 3 瓶" "3" \
  "$(sql "SELECT COALESCE(SUM(qty),0) FROM order_item WHERE order_id=$ORDER3_ID AND is_gift=1")"
mock_pay "$(json_get "$ORDER3" payNo)" >/dev/null
assert_eq "订单状态=待发货" "1" "$(sql "SELECT status FROM orders WHERE id=$ORDER3_ID")"
if [ "$ORDER3_ONLINE" -gt 0 ]; then
  assert_eq "订货在线支付给上级 5 万档提成 10%" "$((ORDER3_ONLINE / 10))" \
    "$(sql "SELECT COALESCE(SUM(amount),0) FROM commission_record WHERE to_store_id=$A_ID AND biz_type='ORDER' AND biz_no='$(sql "SELECT order_no FROM orders WHERE id=$ORDER3_ID")'")"
else
  assert_eq "预存全额抵扣不产生订货提成" "0" \
    "$(sql "SELECT COUNT(*) FROM commission_record WHERE from_store_id=$A_ID AND biz_type='ORDER'")"
fi
[ "$ORDER3_DEPOSIT" -gt 0 ] && ok "预存抵扣 $ORDER3_DEPOSIT 分" || bad "预存抵扣" "未使用预存"

echo "== 15. 数据隔离：不能操作他人订单 =="
CROSS=$(curl -s -X POST "$API/orders/$ORDER3_ID/cancel" -H "Authorization: Bearer $B_TOKEN")
assert_contains "跨门店操作他人订单被拒绝" "$CROSS" '"code":30001'

echo "== 16. 后台约束：非最高档不能开启专属权益 =="
LEVEL_VIOLATION=$(curl -s -X PUT "$ADMIN/levels/1" -H "$AH" -H 'Content-Type: application/json' \
  -d '{"name":"1万档","fee":1000000,"sort":1,"upgradeDays":60,"longTerm":1,"hasBonus":1,"hasGift":1}')
assert_contains "非最高档拒绝开启权益" "$LEVEL_VIOLATION" "10001"

echo "== 17. 冻结门店：不能下单 =="
curl -s -X POST "$ADMIN/stores/$A_ID/freeze" -H "$AH" -H 'Content-Type: application/json' -d '{"reason":"E2E 冻结"}' >/dev/null
assert_eq "冻结后门店状态=2" "2" "$(sql "SELECT status FROM store WHERE id=$A_ID")"
FROZEN=$(curl -s -X POST "$API/orders" -H "$AH_S" -H 'Content-Type: application/json' \
  -d "{\"cartItemIds\":[$CART3_ID],\"receiverName\":\"张三\",\"receiverPhone\":\"$A_PHONE\",\"receiverAddr\":\"杭州市西湖区测试路1号\"}")
assert_contains "冻结门店不能下单" "$FROZEN" '"code":20002'
curl -s -X POST "$ADMIN/stores/$A_ID/unfreeze" -H "$AH" -H 'Content-Type: application/json' -d '{"reason":"E2E 解冻"}' >/dev/null
assert_eq "解冻后门店状态=1" "1" "$(sql "SELECT status FROM store WHERE id=$A_ID")"

echo "== 18. 对账 =="
RECONCILE=$(curl -s "$ADMIN/finance/reconcile" -H "$AH")
assert_contains "对账平衡" "$RECONCILE" '"balanced":true'

echo "== 19. 专项：窗口边界（now = upgrade_deadline 仍可补差） =="
sql "UPDATE store SET level_id=1, level_paid_amount=1000000, upgrade_deadline=DATE_ADD(NOW(), INTERVAL 1 DAY) WHERE id=$A_ID" >/dev/null
OPTIONS=$(curl -s "$API/purchase/options" -H "$AH_S")
assert_contains "窗口期内报价为差额" "$OPTIONS" '"action":"UPGRADE"'
BOUND=$(curl -s -X POST "$API/purchase/orders" -H "$AH_S" -H 'Content-Type: application/json' -d '{"levelId":2,"agreed":true}')
assert_eq "边界当天按差额收款" "2000000" "$(json_num "$BOUND" amount)"
BOUND_PAYNO=$(json_get "$BOUND" payNo)
mock_pay "$BOUND_PAYNO" >/dev/null
# 边界口径：deadline 恰好等于当前时刻仍判为窗口内（机会均等，故用 1 秒后边界逼近）
sql "UPDATE store SET level_id=1, level_paid_amount=1000000, upgrade_deadline=DATE_ADD(NOW(), INTERVAL 1 SECOND) WHERE id=$A_ID" >/dev/null
BOUND2=$(curl -s -X POST "$API/purchase/orders" -H "$AH_S" -H 'Content-Type: application/json' -d '{"levelId":2,"agreed":true}')
assert_eq "窗口临界仍按差额收款" "2000000" "$(json_num "$BOUND2" amount)"
mock_pay "$(json_get "$BOUND2" payNo)" >/dev/null
sql "UPDATE store SET upgrade_deadline=DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id=$A_ID" >/dev/null
OVERDUE=$(curl -s -X POST "$API/purchase/orders" -H "$AH_S" -H 'Content-Type: application/json' -d '{"levelId":3,"agreed":true}')
assert_contains "过窗口期报价改为全额预存" "$OVERDUE" '"bizType":"RECHARGE"'
assert_eq "过窗口期全额收款 5000000" "5000000" "$(json_num "$OVERDUE" amount)"
mock_pay "$(json_get "$OVERDUE" payNo)" >/dev/null

echo "== 20. 专项：活动限次只统计已支付预存单，补差不占次数 =="
# 活动覆盖 1/3 两个档位：3 万档门店参加按「全额预存」（不升档，故不占补差判定），1 万档用于补差判定
curl -s -X POST "$ADMIN/recharge-activities" -H "$AH" -H 'Content-Type: application/json' \
  -d '{"title":"E2E-活动","limitPerStore":1,"status":1,"levelIds":[1]}' >/dev/null
ACT_ID=$(sql "SELECT id FROM recharge_activity ORDER BY id DESC LIMIT 1")
assert_eq "活动创建成功" "1" "$(sql "SELECT COUNT(*) FROM recharge_activity WHERE id=$ACT_ID")"
# 门店回到 1 万档且窗口期内：选 3 万档应为补差（UPGRADE），补差不占活动次数
sql "UPDATE store SET level_id=1, level_paid_amount=1000000, upgrade_deadline=DATE_ADD(NOW(), INTERVAL 30 DAY) WHERE id=$A_ID" >/dev/null
DIFF_ACT=$(curl -s -X POST "$API/purchase/orders" -H "$AH_S" -H 'Content-Type: application/json' \
  -d "{\"levelId\":3,\"activityId\":$ACT_ID,\"agreed\":true}")
assert_contains "窗口期内选高档走补差单" "$DIFF_ACT" '"bizType":"UPGRADE"'
assert_eq "补差不写预存单" "0" "$(sql "SELECT COUNT(*) FROM recharge_order WHERE store_id=$A_ID AND activity_id=$ACT_ID")"
mock_pay "$(json_get "$DIFF_ACT" payNo)" >/dev/null
# 降档到 1 万档后参加活动（RECHARGE），支付后占用 1 次
sql "UPDATE store SET level_id=3, level_paid_amount=5000000 WHERE id=$A_ID" >/dev/null
ACT1=$(curl -s -X POST "$API/purchase/orders" -H "$AH_S" -H 'Content-Type: application/json' \
  -d "{\"levelId\":1,\"activityId\":$ACT_ID,\"agreed\":true}")
assert_contains "首次参加活动创建预存单" "$ACT1" '"bizType":"RECHARGE"'
mock_pay "$(json_get "$ACT1" payNo)" >/dev/null
assert_eq "活动首次已支付预存单 1 笔" "1" \
  "$(sql "SELECT COUNT(*) FROM recharge_order WHERE store_id=$A_ID AND activity_id=$ACT_ID AND status=1")"
ACT2=$(curl -s -X POST "$API/purchase/orders" -H "$AH_S" -H 'Content-Type: application/json' \
  -d "{\"levelId\":1,\"activityId\":$ACT_ID,\"agreed\":true}")
assert_contains "超出活动限次被拒绝" "$ACT2" "已用完"
curl -s -X POST "$ADMIN/recharge-activities/$ACT_ID/status?status=0" -H "$AH" >/dev/null
OFFLINE_ACT=$(curl -s -X POST "$API/purchase/orders" -H "$AH_S" -H 'Content-Type: application/json' \
  -d "{\"levelId\":1,\"activityId\":$ACT_ID,\"agreed\":true}")
assert_contains "活动下架后不可再参加" "$OFFLINE_ACT" "已下架"
OFFLINE_ENTRY=$(curl -s "$API/purchase/options" -H "$AH_S")
assert_contains "活动下架不影响常驻预存入口" "$OFFLINE_ENTRY" '"action":"RECHARGE"'

echo "== 21. 专项：关单后回调进异常，订单本身无异常态 =="
CART4=$(curl -s -X POST "$API/cart/items" -H "$AH_S" -H 'Content-Type: application/json' -d '{"skuId":1,"qty":1}')
CART4_ID=$(json_num "$CART4" id)
ORDER4=$(curl -s -X POST "$API/orders" -H "$AH_S" -H 'Content-Type: application/json' \
  -d "{\"cartItemIds\":[$CART4_ID],\"useDeposit\":false,\"receiverName\":\"张三\",\"receiverPhone\":\"$A_PHONE\",\"receiverAddr\":\"杭州市西湖区测试路1号\"}")
ORDER4_ID=$(json_num "$ORDER4" id)
ORDER4_PAYNO=$(json_get "$ORDER4" payNo)
curl -s -X POST "$API/orders/$ORDER4_ID/cancel" -H "$AH_S" >/dev/null
assert_eq "关单后订单为已取消" "4" "$(sql "SELECT status FROM orders WHERE id=$ORDER4_ID")"
assert_eq "关单后支付单为已关闭" "4" "$(sql "SELECT status FROM payment WHERE pay_no='$ORDER4_PAYNO'")"
DEPOSIT_BEFORE4=$(deposit_of "$A_ID")
# 走真实回调入口 /notify/saobei，对已关闭支付单应置成功并转异常（不篡改订单）
NOTIFY4=$(mock_notify "$ORDER4_PAYNO" "$(sql "SELECT amount FROM payment WHERE pay_no='$ORDER4_PAYNO'")")
assert_contains "关单后回调被受理" "$NOTIFY4" '"return_code":"01'
assert_eq "关单后回调不改订单状态" "4" "$(sql "SELECT status FROM orders WHERE id=$ORDER4_ID")"
assert_eq "关单后回调不重复退款" "$DEPOSIT_BEFORE4" "$(deposit_of "$A_ID")"
assert_eq "关单后回调仅留异常事件" "1" \
  "$(sql "SELECT COUNT(*) FROM store_event WHERE event_type='PAY_ABNORMAL' AND store_id=$A_ID AND biz_no='$(sql "SELECT order_no FROM orders WHERE id=$ORDER4_ID")'")"
assert_eq "订单表无异常态取值" "0" "$(sql "SELECT COUNT(*) FROM orders WHERE status NOT IN (0,1,2,3,4)")"

echo "== 22. 专项：异常单处置留痕（补开通 / 原路退款不写流水） =="
ABN_LIST=$(curl -s "$ADMIN/finance/abnormal-orders?type=JOIN" -H "$AH")
assert_contains "异常单列表可查询" "$ABN_LIST" '"code":0'
# 注册一家未加盟门店（拿到真实 wallet），代客加盟后把单据置为已关闭，再走支付回调 → 异常单
D_PHONE="138$(printf '%08d' $((SUFFIX + 3)))"
D_TOKEN=$(json_get "$(register "$D_PHONE" "异常店")" data.token)
ABN_STORE=$(sql "SELECT id FROM store WHERE phone='$D_PHONE'")
assert_eq "异常店钱包已初始化" "1" "$(sql "SELECT COUNT(*) FROM wallet WHERE store_id=$ABN_STORE")"
ABN_JOIN=$(curl -s -X POST "$ADMIN/purchase/proxy-join" -H "$AH" -H 'Content-Type: application/json' \
  -d "{\"storeId\":$ABN_STORE,\"levelId\":1,\"offlineMethod\":1,\"proofImages\":[\"p.png\"],\"idImages\":[\"i.png\"],\"agreementImages\":[\"a.png\"]}")
ABN_JOIN_NO=$(json_get "$ABN_JOIN" orderNo)
assert_eq "代客加盟生成加盟单" "1" "$(sql "SELECT COUNT(*) FROM join_order WHERE order_no='$ABN_JOIN_NO'")"
ABN_JOIN_ID=$(sql "SELECT id FROM join_order WHERE order_no='$ABN_JOIN_NO'")
ABN_PAYNO=$(json_get "$ABN_JOIN" payNo)
ABN_AMOUNT=$(sql "SELECT amount FROM payment WHERE pay_no='$ABN_PAYNO'")
# 模拟「关单后才到达的成功回调」：业务单已取消（加盟单取消态=2）、支付单已关闭
sql "UPDATE join_order SET status=2 WHERE id=$ABN_JOIN_ID" >/dev/null
sql "UPDATE payment SET status=4 WHERE pay_no='$ABN_PAYNO'" >/dev/null
mock_notify "$ABN_PAYNO" "$ABN_AMOUNT" >/dev/null
assert_eq "关单后回调把加盟单置异常" "3" "$(sql "SELECT status FROM join_order WHERE id=$ABN_JOIN_ID")"
ABN_LOGS_BEFORE=$(sql "SELECT COUNT(*) FROM wallet_log WHERE store_id=$ABN_STORE")
RESOLVE=$(curl -s -X POST "$ADMIN/finance/abnormal-orders/JOIN/$ABN_JOIN_ID/resolve" -H "$AH" \
  -H 'Content-Type: application/json' -d '{"action":"REFUND","remark":"E2E 原路退款"}')
assert_contains "异常单原路退款成功" "$RESOLVE" '"code":0'
assert_eq "原路退款不写余额流水" "$ABN_LOGS_BEFORE" "$(sql "SELECT COUNT(*) FROM wallet_log WHERE store_id=$ABN_STORE")"
assert_eq "原路退款写入退款事件" "1" \
  "$(sql "SELECT COUNT(*) FROM store_event WHERE store_id=$ABN_STORE AND event_type='PAY_REFUND' AND biz_no='$ABN_JOIN_NO'")"
assert_eq "支付单置已退款" "5" "$(sql "SELECT status FROM payment WHERE pay_no='$ABN_PAYNO'")"
AGAIN=$(curl -s -X POST "$ADMIN/finance/abnormal-orders/JOIN/$ABN_JOIN_ID/resolve" -H "$AH" \
  -H 'Content-Type: application/json' -d '{"action":"REFUND","remark":"重复处置"}')
assert_contains "非异常单不可处置" "$AGAIN" "异常单不存在或已处置"

echo "== 23. 专项：特批（代客加盟/线下收款）留痕 =="
assert_eq "代客加盟写档位变更流水" "1" \
  "$(sql "SELECT COUNT(*) FROM store_level_log WHERE store_id=$ABN_STORE AND biz_no='$ABN_JOIN_NO'")"
assert_eq "代客加盟写门店事件" "1" \
  "$(sql "SELECT COUNT(*) FROM store_event WHERE store_id=$ABN_STORE AND biz_no='$ABN_JOIN_NO' AND event_type='JOIN'")"
assert_eq "代客加盟记录操作人" "1" \
  "$(sql "SELECT COUNT(*) FROM store_event WHERE store_id=$ABN_STORE AND biz_no='$ABN_JOIN_NO' AND event_type='JOIN' AND operator_id>0")"
assert_eq "代客加盟签署纸质协议" "1" \
  "$(sql "SELECT COUNT(*) FROM store_agreement WHERE store_id=$ABN_STORE")"

echo
echo "==================== 结果 ===================="
echo "通过 $PASS 项，失败 $FAIL 项"
[ "$FAIL" -eq 0 ] || exit 1
