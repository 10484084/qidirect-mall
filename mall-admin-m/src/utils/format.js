/**
 * 金额：整数分 → 元，保留两位小数。
 */
export function fen2yuan(fen) {
  const n = Number(fen || 0);
  return (n / 100).toFixed(2);
}

/**
 * 金额：元 → 整数分。
 */
export function yuan2fen(yuan) {
  return Math.round(Number(yuan || 0) * 100);
}

/**
 * 比例：0.10 → 10.00（后端用 BigDecimal，0.1 表示 10%）。
 */
export function ratio2percent(ratio) {
  return (Number(ratio || 0) * 100).toFixed(2);
}

export function percent2ratio(percent) {
  return Number(percent || 0) / 100;
}

export function toQuery(params, extra = {}) {
  const query = { ...extra };
  Object.entries(params || {}).forEach(([k, v]) => {
    if (v !== '' && v !== null && v !== undefined) query[k] = v;
  });
  // 起止时间必须成对出现，否则后端时间范围条件不完整
  if (('startTime' in query) !== ('endTime' in query)) {
    delete query.startTime;
    delete query.endTime;
  }
  return query;
}
