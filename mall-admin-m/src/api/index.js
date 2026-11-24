import { get, post } from './request';

export const authApi = {
  login: (data) => post('/auth/login', data)
};

export const dashboardApi = {
  summary: () => get('/dashboard/summary')
};

export const levelApi = {
  list: () => get('/levels')
};

export const storeApi = {
  page: (params) => get('/stores', params),
  detail: (id) => get(`/stores/${id}`),
  resetPhone: (id, phone) => post(`/stores/${id}/reset-phone`, { phone }),
  resetPassword: (id) => post(`/stores/${id}/reset-password`),
  freeze: (id, reason) => post(`/stores/${id}/freeze`, { reason }),
  unfreeze: (id, reason) => post(`/stores/${id}/unfreeze`, { reason }),
  inviteQr: (id) => get(`/stores/${id}/invite-qr`),
  events: (id, params) => get(`/stores/${id}/events`, params)
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
