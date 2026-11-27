import { del, get, post, put, uploadFile } from './request';

export const authApi = {
  login: (data) => post('/auth/login', data),
  sendResetCode: (data) => post('/auth/reset-code', data),
  resetPassword: (data) => post('/auth/reset-password', data)
};

export const dashboardApi = {
  summary: () => get('/dashboard/summary')
};

export const levelApi = {
  list: () => get('/levels')
};

export const storeApi = {
  factoryInviteQr: () => get('/stores/invite-qr'),
  page: (params) => get('/stores', params),
  detail: (id) => get(`/stores/${id}`),
  resetPhone: (id, phone) => post(`/stores/${id}/reset-phone`, { phone }),
  resetPassword: (id) => post(`/stores/${id}/reset-password`),
  freeze: (id, reason) => post(`/stores/${id}/freeze`, { reason }),
  unfreeze: (id, reason) => post(`/stores/${id}/unfreeze`, { reason }),
  inviteQr: (id) => get(`/stores/${id}/invite-qr`),
  events: (id, params) => get(`/stores/${id}/events`, params)
};

export const giftRuleApi = {
  list: (productId) => get('/gift-rules', productId ? { productId } : undefined),
  create: (data) => post('/gift-rules', data),
  update: (id, data) => put(`/gift-rules/${id}`, data),
  setEnabled: (id, enabled) => put(`/gift-rules/${id}/enabled`, null, { params: { enabled } }),
  remove: (id) => del(`/gift-rules/${id}`)
};

export const productApi = {
  page: (params) => get('/products', params),
  detail: (id) => get(`/products/${id}`),
  update: (id, data) => put(`/products/${id}`, data),
  setStatus: (id, status) => put(`/products/${id}/status`, null, { params: { status } }),
  create: (data) => post('/products', data),
  sort: (ids) => put('/products/sort', { ids })
};

export { uploadFile };

export const categoryApi = {
  list: () => get('/product-categories'),
  sort: (ids) => put('/product-categories/sort', { ids })
};

export const orderApi = {
  page: (params) => get('/orders', params),
  detail: (id) => get(`/orders/${id}`),
  ship: (id, data) => post(`/orders/${id}/ship`, data)
};

export const withdrawApi = {
  page: (params) => get('/withdraws', params),
  detail: (id) => get(`/withdraws/${id}`),
  approve: (id) => post(`/withdraws/${id}/approve`),
  reject: (id, reason) => post(`/withdraws/${id}/reject`, { reason }),
  paid: (id, proofImages) => post(`/withdraws/${id}/paid`, { proofImages }),
  fail: (id, reason) => post(`/withdraws/${id}/fail`, { reason })
};
