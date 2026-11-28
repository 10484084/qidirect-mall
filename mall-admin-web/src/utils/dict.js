export const ORDER_STATUS = {
  0: { label: '待支付', type: 'warning' },
  1: { label: '待发货', type: 'primary' },
  2: { label: '已发货', type: 'success' },
  3: { label: '已完成', type: 'info' },
  4: { label: '已取消', type: 'info' }
};

export const ORDER_STATUS_OPTIONS = Object.entries(ORDER_STATUS).map(([value, v]) => ({
  value: Number(value),
  label: v.label
}));

export const PAY_STATUS = {
  0: { label: '待支付', type: 'warning' },
  1: { label: '支付中', type: 'primary' },
  2: { label: '成功', type: 'success' },
  3: { label: '失败', type: 'danger' },
  4: { label: '已关闭', type: 'info' },
  5: { label: '已退款', type: 'info' }
};

export const PAY_STATUS_OPTIONS = Object.entries(PAY_STATUS).map(([value, v]) => ({
  value: Number(value),
  label: v.label
}));

export const PAY_CHANNEL = { 1: 'JSAPI', 2: '聚合码', 3: '线下收款' };
export const PAY_CHANNEL_OPTIONS = Object.entries(PAY_CHANNEL).map(([value, label]) => ({
  value: Number(value),
  label
}));

export const OFFLINE_METHOD = { 1: '现金', 2: '转账', 3: '刷卡' };
export const OFFLINE_METHOD_OPTIONS = Object.entries(OFFLINE_METHOD).map(([value, label]) => ({
  value: Number(value),
  label
}));

export const BIZ_TYPE = {
  JOIN: '开通',
  UPGRADE: '升档补差',
  RECHARGE: '预存',
  OFFLINE: '线下收款',
  ORDER: '商品订单'
};
export const BIZ_TYPE_OPTIONS = Object.entries(BIZ_TYPE).map(([value, label]) => ({
  value,
  label
}));

export const WITHDRAW_STATUS = {
  0: { label: '待审核', type: 'warning' },
  1: { label: '待打款', type: 'primary' },
  2: { label: '已打款', type: 'success' },
  3: { label: '已驳回', type: 'danger' },
  4: { label: '打款失败', type: 'danger' }
};

export const WITHDRAW_STATUS_OPTIONS = Object.entries(WITHDRAW_STATUS).map(([value, v]) => ({
  value: Number(value),
  label: v.label
}));

export const BONUS_STATUS = {
  0: { label: '待生成', type: 'warning' },
  1: { label: '已入账', type: 'success' },
  2: { label: '已冲正', type: 'danger' }
};

export const STORE_STATUS = { 1: { label: '正常', type: 'success' }, 2: { label: '冻结', type: 'danger' } };
export const STORE_STATUS_OPTIONS = Object.entries(STORE_STATUS).map(([value, v]) => ({
  value: Number(value),
  label: v.label
}));

export const AUTH_STATUS = {
  0: { label: '未填写', type: 'info' },
  1: { label: '已填写', type: 'warning' },
  2: { label: '已核验', type: 'success' }
};
export const AUTH_STATUS_OPTIONS = Object.entries(AUTH_STATUS).map(([value, v]) => ({
  value: Number(value),
  label: v.label
}));

export const PARENT_CHANGE_STATUS = {
  0: { label: '待审批', type: 'warning' },
  1: { label: '已通过', type: 'success' },
  2: { label: '已驳回', type: 'danger' }
};
export const PARENT_CHANGE_STATUS_OPTIONS = Object.entries(PARENT_CHANGE_STATUS).map(
  ([value, v]) => ({ value: Number(value), label: v.label })
);

export const COMMISSION_STATUS = {
  1: { label: '已入账', type: 'success' },
  2: { label: '已冲正', type: 'danger' }
};

export const COMMISSION_LEVEL = { 1: '一级', 2: '二级' };

export const WALLET_ACCOUNT = { 1: '预存余额', 2: '佣金余额' };
export const WALLET_ACCOUNT_OPTIONS = Object.entries(WALLET_ACCOUNT).map(([value, label]) => ({
  value: Number(value),
  label
}));

export const SHIP_TYPE = { 1: '物流发货', 2: '其他方式' };
export const SHIP_TYPE_OPTIONS = Object.entries(SHIP_TYPE).map(([value, label]) => ({
  value: Number(value),
  label
}));

export const NOTICE_TARGET = { 1: '全部门店', 2: '指定门店' };

export const ABNORMAL_TYPE = { JOIN: '开通/升级异常单', RECHARGE: '预存异常单' };
export const ABNORMAL_TYPE_OPTIONS = Object.entries(ABNORMAL_TYPE).map(([value, label]) => ({ value, label }));
export const ABNORMAL_RESOLVE = { REOPEN: '重新打开', REFUND: '退款', CLOSE: '关闭' };
export const ABNORMAL_RESOLVE_OPTIONS = Object.entries(ABNORMAL_RESOLVE).map(([value, label]) => ({ value, label }));

export function statusMeta(map, value) {
  return map[value] || { label: String(value ?? '-'), type: 'info' };
}
