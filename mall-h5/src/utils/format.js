/**
 * 金额单位统一为「分」，展示时转元。
 */
export function fenToYuan(fen) {
  if (fen === null || fen === undefined || fen === '') return '0.00';
  return (Number(fen) / 100).toFixed(2);
}

export function yuanToFen(value) {
  const num = Number(value);
  if (!Number.isFinite(num)) return 0;
  return Math.round(num * 100);
}

export function money(fen) {
  return `¥${fenToYuan(fen)}`;
}

export function orderStatusText(status) {
  return {
    0: '待支付',
    1: '待发货',
    2: '已发货',
    3: '已完成',
    4: '已取消'
  }[status] || '未知';
}

export function orderStatusType(status) {
  return {
    0: 'warning',
    1: 'primary',
    2: 'primary',
    3: 'success',
    4: 'default'
  }[status] || 'default';
}

export function withdrawStatusText(status) {
  return {
    0: '待审核',
    1: '待打款',
    2: '已打款',
    3: '已驳回',
    4: '打款失败'
  }[status] || '未知';
}

export function withdrawStatusType(status) {
  return {
    0: 'warning',
    1: 'primary',
    2: 'success',
    3: 'danger',
    4: 'danger'
  }[status] || 'default';
}

export function actionText(action) {
  return {
    JOIN: '首次加盟',
    UPGRADE: '窗口期补差',
    RECHARGE: '全额预存'
  }[action] || action;
}

export function bizTypeText(bizType) {
  return {
    JOIN: '加盟',
    UPGRADE: '补差升档',
    RECHARGE: '预存',
    ORDER_BALANCE: '订单抵扣',
    ORDER_ONLINE: '订单支付',
    ORDER_REFUND: '订单退回',
    COMMISSION: '提成',
    BONUS: '业绩奖',
    BONUS_REVERSAL: '业绩奖冲正',
    WITHDRAW: '提现打款',
    OFFLINE: '线下收款',
    ADJUST: '人工调整'
  }[bizType] || bizType;
}

export function eventTypeText(type) {
  return {
    JOIN_PAID: '加盟成功',
    UPGRADE_PAID: '升档成功',
    RECHARGE_PAID: '预存成功',
    OFFLINE_PAID: '线下收款',
    ORDER_PAID: '订单支付',
    ORDER_CANCEL: '订单取消',
    ORDER_SHIP: '订单发货',
    ORDER_CONFIRM: '确认收货',
    COMMISSION: '提成到账',
    BONUS: '业绩奖到账',
    BONUS_REVERSAL: '业绩奖冲正',
    WITHDRAW_APPLY: '提现申请',
    WITHDRAW_AUDIT: '提现审核',
    WITHDRAW_PAID: '提现打款',
    WITHDRAW_FAIL: '打款失败',
    BALANCE_ADJUST: '余额调整',
    AUTH_SUBMIT: '实名提交',
    STORE_FREEZE: '门店冻结',
    STORE_UNFREEZE: '门店解冻',
    PAY_ABNORMAL: '支付异常',
    JOIN: '加盟',
    RECHARGE: '预存',
    ORDER: '订货',
    BONUS: '业绩',
    WITHDRAW: '提现'
  }[type] || type;
}

export function formatTime(value) {
  if (!value) return '';
  return String(value).replace('T', ' ').slice(0, 16);
}

export function todayMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

export function recentMonths(count = 12) {
  const list = [];
  const now = new Date();
  for (let i = 0; i < count; i += 1) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    const value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    list.push({ text: value, value });
  }
  return list;
}
