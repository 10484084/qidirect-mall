import { del, download, get, post, put } from './request';

export const authApi = {
  login: (data) => post('/auth/login', data),
  sendAdminResetCode: (data) => post('/auth/reset-code', data),
  resetAdminPassword: (data) => post('/auth/reset-password', data)
};

export const dashboardApi = {
  summary: () => get('/dashboard/summary')
};

export const storeApi = {
  page: (params) => get('/stores', params),
  tree: (parentId) => get('/stores/tree', parentId ? { parentId } : undefined),
  detail: (id) => get(`/stores/${id}`),
  create: (data) => post('/stores', data),
  resetPhone: (id, phone) => post(`/stores/${id}/reset-phone`, { phone }),
  resetPassword: (id) => post(`/stores/${id}/reset-password`),
  freeze: (id, reason) => post(`/stores/${id}/freeze`, { reason }),
  unfreeze: (id, reason) => post(`/stores/${id}/unfreeze`, { reason }),
  inviteQr: (id) => get(`/stores/${id}/invite-qr`),
  overrideBank: (id, data) => post(`/stores/${id}/bank`, data),
  events: (id, params) => get(`/stores/${id}/events`, params)
};

export const levelApi = {
  list: () => get('/levels'),
  create: (data) => post('/levels', data),
  update: (id, data) => put(`/levels/${id}`, data),
  changeStatus: (id, status) => post(`/levels/${id}/status`, null, { params: { status } }),
  remove: (id) => del(`/levels/${id}`)
};

export const categoryApi = {
  list: () => get('/product-categories'),
  sort: (ids) => put('/product-categories/sort', { ids }),
  create: (data) => post('/product-categories', data),
  update: (id, data) => put(`/product-categories/${id}`, data),
  remove: (id) => del(`/product-categories/${id}`)
};

export const productApi = {
  sort: (ids) => put('/products/sort', { ids }),
  setStatus: (id, status) => put(`/products/${id}/status`, null, { params: { status } }),
  page: (params) => get('/products', params),
  detail: (id) => get(`/products/${id}`),
  create: (data) => post('/products', data),
  update: (id, data) => put(`/products/${id}`, data),
  remove: (id) => del(`/products/${id}`)
};

export const giftRuleApi = {
  list: (productId) => get('/gift-rules', productId ? { productId } : undefined),
  create: (data) => post('/gift-rules', data),
  update: (id, data) => put(`/gift-rules/${id}`, data),
  setEnabled: (id, enabled) => put(`/gift-rules/${id}/enabled`, null, { params: { enabled } }),
  remove: (id) => del(`/gift-rules/${id}`)
};

export const agreementApi = {
  list: () => get('/agreements'),
  create: (data) => post('/agreements', data),
  update: (id, data) => put(`/agreements/${id}`, data),
  changeStatus: (id, status) => post(`/agreements/${id}/status`, null, { params: { status } })
};

export const orderApi = {
  page: (params) => get('/orders', params),
  detail: (id) => get(`/orders/${id}`),
  ship: (id, data) => post(`/orders/${id}/ship`, data),
  exportExcel: (params) => download('/orders/export', params, 'orders.xlsx')
};

export const cancelAuditApi = {
  page: (params) => get('/order-cancels', params),
  audit: (id, data) => post(`/order-cancels/${id}/audit`, data)
};

export const purchaseApi = {
  proxyJoin: (data) => post('/purchase/proxy-join', data),
  offlineCollect: (data) => post('/purchase/offline-collect', data)
};

export const walletApi = {
  adjust: (data) => post('/wallets/adjust', data)
};

export const financeApi = {
  walletLogs: (params) => get('/finance/wallet-logs', params),
  payments: (params) => get('/finance/payments', params),
  notifies: (payNo) => get(`/finance/payments/${payNo}/notifies`),
  reconcile: () => get('/finance/reconcile'),
  abnormalOrders: (params) => get('/finance/abnormal-orders', params),
  resolveAbnormal: (type, id, data) => post(`/finance/abnormal-orders/${type}/${id}/resolve`, data)
};

export const commissionApi = {
  config: () => get('/commission/config'),
  updateConfig: (data) => put('/commission/config', data),
  records: (params) => get('/commission/records', params),
  storeDetail: (storeId) => get(`/commission/stores/${storeId}`),
  storeRecords: (storeId, params) => get(`/commission/stores/${storeId}/records`, params)
};

export const performanceApi = {
  rules: () => get('/performance/rules'),
  createRule: (data) => post('/performance/rules', data),
  updateRule: (id, data) => put(`/performance/rules/${id}`, data),
  deleteRule: (id) => del(`/performance/rules/${id}`),
  records: (params) => get('/performance/records', params),
  bonuses: (params) => get('/performance/bonuses', params),
  adjust: (data) => post('/performance/adjust', data),
  reverse: (id, data) => post(`/performance/bonuses/${id}/reverse`, data),
  settle: (month) => post('/performance/settle', null, { params: { month } })
};

export const withdrawApi = {
  page: (params) => get('/withdraws', params),
  batches: (params) => get('/withdraws/batches', params),
  createBatch: (ids) => post('/withdraws/batches', { ids }),
  exportBatch: (id) => download(`/withdraws/batches/${id}/export`, undefined, `withdraw_batch_${id}.csv`),
  detail: (id) => get(`/withdraws/${id}`),
  approve: (id) => post(`/withdraws/${id}/approve`),
  reject: (id, reason) => post(`/withdraws/${id}/reject`, { reason }),
  paid: (id, proofImages) => post(`/withdraws/${id}/paid`, { proofImages }),
  fail: (id, reason) => post(`/withdraws/${id}/fail`, { reason })
};

export const noticeApi = {
  page: (params) => get('/notices', params),
  send: (data) => post('/notices/send', data)
};

export const smsTemplateApi = {
  list: () => get('/sms-templates'),
  create: (data) => post('/sms-templates', data),
  update: (id, data) => put(`/sms-templates/${id}`, data)
};

export const adminUserApi = {
  page: (params) => get('/admin-users', params),
  create: (data) => post('/admin-users', data),
  update: (id, data) => put(`/admin-users/${id}`, data)
};

export const roleApi = {
  list: () => get('/roles'),
  create: (data) => post('/roles', data),
  update: (id, data) => put(`/roles/${id}`, data)
};

export const configApi = {
  list: () => get('/configs'),
  update: (key, data) => put(`/configs/${key}`, data)
};

export const operationLogApi = {
  page: (params) => get('/operation-logs', params)
};
