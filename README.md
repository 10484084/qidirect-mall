# 直销（加盟）商城

一期工程。门店端微信 H5 + 厂家端 Web 后台 + 多模块后端 + 定时任务。

业务口径见 [spec.md](.trae/specs/build-direct-mall-phase1/spec.md)，任务拆分见 [tasks.md](.trae/specs/build-direct-mall-phase1/tasks.md)。

## 模块

| 模块 | 说明 | 端口 |
|---|---|---|
| `mall-common` | 统一返回体、全局异常、错误码、常量枚举、JWT | — |
| `mall-domain` | 实体、DTO/VO、领域服务接口 | — |
| `mall-infra` | MyBatis-Plus、Redis、Redisson、安全鉴权、文件存储、短信 | — |
| `mall-admin` | 厂家端接口 | 8080（`/admin`） |
| `mall-api` | 门店端接口 | 8081（`/api`） |
| `mall-job` | 定时任务，无 Web 端口 | — |
| `mall-h5` | 门店端微信 H5（Vue 3 + Vant 4） | 5173（开发） |
| `mall-admin-web` | 厂家端 Web 后台（Vue 3 + Element Plus） | 5174（开发，`/admin-web/`） |

技术栈：JDK 17、Spring Boot 3.2、MyBatis-Plus 3.5、MySQL 8、Redis 7、Spring Security + JWT、Redisson、Knife4j、EasyExcel。

## 快速启动（Docker Compose）

```bash
cp .env.example .env
docker compose up --build
```

启动后：

- 健康检查：`http://localhost/admin/health`、`http://localhost/api/health`
- Nginx 入口：`http://localhost/api/*` → mall-api，`http://localhost/admin/*` → mall-admin
- 未携带 Token 访问受保护接口返回 401 与统一返回体

首次启动 MySQL 会依次执行 `mysql-init/01-schema.sql` 与 `02-init-data.sql`：创建一期全量表结构，并写入档位、管理员、示例商品与三档价、业绩规则、协议与默认配置。

默认管理员：`admin / admin123`（上线前必须修改）。

## 生产部署

### 1. 准备证书与静态资源

```bash
mkdir -p nginx/certs frontend-dist
# 放置证书（文件名固定）
#   nginx/certs/cert.pem （含证书链）
#   nginx/certs/key.pem  （私钥）

# 构建两端前端，产物输出到 frontend-dist/h5、frontend-dist/pay-bridge 与 frontend-dist/admin-web
docker compose run --rm frontend
```

`frontend` 服务使用 `Dockerfile.frontend` 多阶段构建 `mall-h5` 与 `mall-admin-web`，`dist` 挂载用于导出产物，不会常驻运行。Nginx 以 `/h5/`、`/admin-web/` 前缀托管这两份静态资源，`/assets/` 走 30 天强缓存；`/pay-bridge/` 托管公众号支付 bridge 页。

### 2. 启动

```bash
cp .env.example .env   # 按需填写 JWT_SECRET、数据库密码、OSS、扫呗、微信等
docker compose up -d --build
```

Nginx 监听 80（仅 `/.well-known/acme-challenge/` 与 301 跳 HTTPS）与 443（HTTPS 终结）。转发关系：

| 前缀 | 目标 |
|---|---|
| `/h5/`、`/admin-web/`、`/pay-bridge/` | `frontend-dist` 静态文件 |
| `/admin/`、`/files/` | `mall-admin:8080` |
| `/api/` | `mall-api:8081` |

### 3. 存储切换（OSS）

默认 `STORAGE_TYPE=local`，文件写入 `uploads/` 并由 `/files/` 访问。切换阿里云 OSS：

```bash
STORAGE_TYPE=oss
STORAGE_PUBLIC_URL=https://cdn.example.com
OSS_ENDPOINT=oss-cn-hangzhou.aliyuncs.com
OSS_BUCKET=your-bucket
OSS_ACCESS_KEY=...
OSS_SECRET_KEY=...
OSS_PUBLIC_URL=https://your-bucket.oss-cn-hangzhou.aliyuncs.com
```

`STORAGE_PUBLIC_URL` 仅作用于本地存储（默认 `/files`）；OSS 存储的访问前缀取 `OSS_PUBLIC_URL`，需填写为可公网访问的 Bucket 域名或自定义 CDN 域名。未配置 OSS 变量而指定 `STORAGE_TYPE=oss` 会导致上传失败。

### 4. 备份

```bash
DB_PASSWORD=你的密码 ./scripts/backup.sh
```

输出到 `BACKUP_DIR`（默认 `./backups`）：`db_<时间戳>.sql.gz`（mysqldump `--single-transaction`）与 `uploads_<时间戳>.tar.gz`（本地上传目录），保留 `KEEP_DAYS`（默认 14）天。建议 crontab 每日凌晨执行：

```cron
0 3 * * * cd /srv/qidirect-mall && DB_PASSWORD=*** BACKUP_DIR=/data/backup KEEP_DAYS=14 ./scripts/backup.sh >> /var/log/qmall-backup.log 2>&1
```

使用 OSS 存储时上传目录不在本机，`uploads` 备份应由 OSS 生命周期/跨区域复制策略承担。

### 5. 配置静态校验

无 Docker 环境可用 `docker compose config` 校验编排与环境变量插值，用 `nginx -t -c nginx/nginx.conf` 校验 Nginx 语法。

### 6. 支付通道：Mock → 扫呗

本地与联调默认 `PAYMENT_PROVIDER=mock`，下单走 `/api/notify/mock/pay/{payNo}` 直接置成功。切换扫呗只需改环境变量（无需改代码），启动前逐项确认：

| 环境变量 | 说明 |
|---|---|
| `PAYMENT_PROVIDER=saobei` | 启用 `SaobeiPayGateway`，同时关闭 Mock 下单入口 |
| `SAOBEI_MERCHANT_NO` / `SAOBEI_TERMINAL_ID` / `SAOBEI_ACCESS_TOKEN` | 商户号、终端号、终端令牌（参与 MD5 签名），任一为空时下单/查单/关单直接报「扫呗支付参数未配置」 |
| `SAOBEI_SERVICE_ID` | 服务商编号，直连商户留空 |
| `SAOBEI_API_BASE` | 网关根地址，默认 `https://pay.lcsw.cn/lcsw`（不含接口路径） |
| `SAOBEI_NOTIFY_URL` | 回调地址，必须为公网 HTTPS 且指向 `/api/notify/saobei` |

回调与签名要点：

- 回调入口 `POST /api/notify/saobei`，支持 form 与 JSON；成功返回 `return_code=01`，验签失败或金额不一致返回 `02` 由扫呗重试。验签规则：除 `key_sign` 外的非空参数按参数名升序拼 `k=v&...`，追加 `&key=<accessToken>` 后取 MD5 小写。
- 回调 `terminal_trace` 即系统 `pay_no`；`total_fee` 必须等于支付单金额，否则拒绝（不置成功）。已关闭/已退款的支付单收到成功回调时，业务单转异常等待后台处置，不会回滚订单状态。
- 全部回调（含验签失败）落 `payment_notify_log`，排查时可直接查原始报文。
- 下单、查单（关单前确认）、关单均在数据库事务外调用网关；关单以查单结果为准，查单显示已支付则不退款、直接补记成功。
- 商户平台无需额外配置授权目录时，扫呗支付走公众号 JSAPI，由我方 bridge 页拉起；关单/查单不依赖授权目录。

微信公众平台配置（公众号 JSAPI 支付必需）：

| 配置项 | 值 |
|---|---|
| 网页授权域名 | `mall.example.com`（与 `WX_BRIDGE_URL` 一致，需上传 `MP_verify_*.txt` 到站点根） |
| JS 接口安全域名 | 同上 |
| 支付授权目录 | `https://mall.example.com/pay-bridge/`（必须与 `WX_BRIDGE_URL` 路径前缀完全一致，含末尾斜杠） |
| `WX_APPID` / `WX_SECRET` | 公众号 AppID 与 AppSecret，用于换取 `openid` |

`/pay-bridge/` 由 Nginx 直接托管在 `frontend-dist/pay-bridge`（见 [nginx/nginx.conf](nginx/nginx.conf)），用于在微信内串联公众号授权与扫呗 JSAPI 支付参数；其源码为 [mall-h5/pay-bridge/index.html](mall-h5/pay-bridge/index.html)，`mall-h5` 的 `npm run build` 会依次产出 `dist/`（H5）与 `dist/pay-bridge/`（bridge 页）。`WX_BRIDGE_URL` 填 `https://mall.example.com/pay-bridge`（访问时固定落到 `/pay-bridge/`，与授权目录一致）。上线前用真实商户号跑一笔 0.01 元订单验证：下单 → 拉起支付 → 回调 `01` → 订单/支付单状态正确 → 后台对账平衡。

## 本地开发

1. 启动依赖（也可自行本地安装）：

```bash
docker compose up -d mysql redis
```

2. 编译打包：

```bash
mvn -DskipTests package
```

3. 分别启动：

```bash
java -jar mall-admin/target/mall-admin.jar
java -jar mall-api/target/mall-api.jar --spring.profiles.active=job
```

> `mall-job` 默认使用 `job` profile，且 `web-application-type=none`。数据库未就绪时启动不阻塞，`sys_config` 默认值会在应用就绪后补齐。

### 定时任务清单（`mall-job`）

| 任务 | 类 | 默认时间 | 说明 |
| --- | --- | --- | --- |
| 支付补偿 | `PaymentCompensateJob` | 每 60 秒 | 按 id 游标分批扫描全部中间态支付单，扫呗 4.5 查单已支付则补记成功 |
| 采购单超时关单 | `PaymentCompensateJob` | 每 60 秒 | 加盟/补差/预存单过期走 4.8 → 4.5 → 取消 |
| 订货单超时关单 | `OrderTimeoutJob` | 每 60 秒 | 超 `order_timeout_minutes` 未支付订货单取消 |
| 业绩月结 | `PerformanceSettleJob` | 每月 1 日 01:30 | 结算上月，逐店幂等，失败清单告警 |
| 提现窗口计算 | `WithdrawWindowJob` | 每日 00:05 | 按 `withdraw_days` 计算今日开放状态与下次开放日，写回 `sys_config` |
| 提现批次超期告警 | `WithdrawBatchAlertJob` | 每日 09:00 | 超 7 天未打款批次告警 |
| 每日对账 | `ReconcileJob` | 每日 02:30 | 预存/佣金/冻结三条断言，不平衡输出差额告警 |

> cron 均可通过环境变量覆盖：`PERFORMANCE_SETTLE_CRON`、`WITHDRAW_WINDOW_CRON`、`WITHDRAW_BATCH_ALERT_CRON`、`RECONCILE_CRON`。

> 使用本机 MySQL/Redis 时，先 `brew services start mysql@8.4 redis`，再导入 `mysql-init/01-schema.sql` 与 `02-init-data.sql`；密码非默认 `root` 时用 `DB_PASSWORD=你的密码 java -jar mall-admin/target/mall-admin.jar` 启动。

4. 门店端 H5：

```bash
cd mall-h5
npm install
VITE_API_TARGET=http://127.0.0.1:8081 npm run dev
```

开发地址 `http://localhost:5173/h5/`，`/api` 由 Vite 代理到 `VITE_API_TARGET`（默认 `http://127.0.0.1:8081`）；生产构建为 `npm run build`，产物 `dist/` 由 Nginx 以 `/h5/` 前缀托管。

非微信环境会展示「请在微信中打开」引导页；本地联调可在开发环境 URL 追加 `?wx=1`（或 `#/...?wx=1`）模拟手机微信，仅 Vite dev 生效，生产构建忽略。JWT 无刷新接口，Token 过期或接口返回 401 时统一跳转登录页。

5. 厂家端 Web 后台：

```bash
cd mall-admin-web
npm install
npm run dev
```

开发地址 `http://localhost:5174/admin-web/`，`/admin` 由 Vite 代理到 `VITE_ADMIN_API_TARGET`（默认 `http://127.0.0.1:8080`）；生产构建为 `npm run build`，产物 `dist/` 由 Nginx 以 `/admin-web/` 前缀托管。菜单按登录返回的 `perms` 控制（含 `*` 视为全部权限），上传接口限制 jpg/png/webp/pdf 且不超过 5MB。

## 接口约定

- 门店端前缀 `/api`，厂家端前缀 `/admin`，鉴权头 `Authorization: Bearer <token>`。
- 统一返回体：`{"code":0,"msg":"ok","data":{}}`。
- 错误码分段：`1xxxx` 通用、`2xxxx` 门店、`3xxxx` 订单、`4xxxx` 钱包提现、`5xxxx` 支付。
- 双端 Token 互不通用：`mall-admin` 只接受 `userType=admin`，`mall-api` 只接受 `userType=store`。
- JWT 有效期默认 7 天，密钥由 `JWT_SECRET` 提供（至少 32 字节）。

## 系统配置（sys_config）

| 配置键 | 默认值 | 说明 |
|---|---|---|
| `commission_rate_1` | `0.10` | 一级提成比例 |
| `commission_rate_2` | `0.03` | 二级提成比例 |
| `withdraw_min_amount` | `10000` | 最低提现金额（分） |
| `withdraw_days` | `15,last` | 提现开放日 |
| `order_timeout_minutes` | `10` | 订单支付超时（分钟） |
| `month_settle_day` | `1` | 业绩月结日 |
| `service_wechat` | 空 | 客服微信 |
| `service_phone` | 空 | 客服电话 |

读取与写入使用 `mall-infra` 的 `SysConfigService`。

## 环境变量

见 [.env.example](.env.example)：数据库、Redis、JWT、扫呗、微信、短信、OSS。支付密钥等敏感信息只通过环境变量注入。

## 当前进度

- [x] 任务 1：工程骨架与基础设施
- [x] 任务 2：数据库 DDL 与初始化脚本
- [x] 任务 3：认证、门店、实名与邀请码
- [x] 任务 4：档位、统一购买、协议与代客加盟
- [x] 任务 5：钱包与流水
- [x] 任务 6：商品、规格、三档价与买赠
- [x] 任务 7：订单、发货与取消
- [x] 任务 8：扫呗支付接入
- [x] 任务 9：两级提成
- [x] 任务 10：业绩奖
- [x] 任务 11：提现
- [x] 任务 12：团队、数据隔离与改上级
- [x] 任务 13：后台基础管理与财务
- [x] 任务 14：门店端 H5（Vue 3 + Vant 4）
- [x] 任务 15：厂家端 Web（Vue 3 + Element Plus）
- [x] 任务 16：部署、联调与验收（`scripts/e2e-regression.sh` 100 项断言 0 失败；扫呗真实商户联调与并发压测待上线环境执行）
- [x] 任务 17：补齐定时任务缺口（每日对账、提现窗口计算；支付补偿与采购超时关单改按 id 游标分批扫描全部中间态支付单）
