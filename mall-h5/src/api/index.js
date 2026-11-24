import { del, get, post, put } from './request';

export const authApi = {
  pwdLogin: (data) => post('/auth/pwd-login', data),
  changePassword: (data) => post('/auth/change-password', data),
  sendSms: (phone) => post('/auth/sms', { phone }),
  register: (data) => post('/auth/register', data),
  login: (data) => post('/auth/login', data)
};

export const storeApi = {
  profile: () => get('/store/profile'),
  updateProfile: (data) => put('/store/profile', data),
  submitAuth: (data) => post('/store/auth', data),
  bindBank: (data) => put('/store/bank', data),
  inviteQr: () => get('/store/invite-qr'),
  parentChange: (data) => post('/store/parent-change', data),
  eligibility: () => get('/store/order-eligibility')
};

export const purchaseApi = {
  options: () => get('/purchase/options'),
  agreement: () => get('/purchase/agreement'),
  create: (data) => post('/purchase/orders', data)
};

export const productApi = {
  categories: () => get('/product-categories'),
  list: (params) => get('/products', params),
  detail: (id) => get(`/products/${id}`),
  giftPreview: (id, skuId, qty) => get(`/products/${id}/gift-preview`, { skuId, qty })
};

export const cartApi = {
  list: () => get('/cart'),
  add: (skuId, qty) => post('/cart/items', { skuId, qty }),
  updateQty: (id, qty) => put(`/cart/items/${id}`, { qty }),
  remove: (id) => del(`/cart/items/${id}`),
  clear: () => del('/cart')
};

export const orderApi = {
  create: (data) => post('/orders', data),
  page: (params) => get('/orders', params),
  detail: (id) => get(`/orders/${id}`),
  cancel: (id) => post(`/orders/${id}/cancel`),
  confirm: (id) => post(`/orders/${id}/confirm`)
};

export const payApi = {
  authorize: (payNo) => get(`/pay/${payNo}/authorize`),
  jsapi: (payNo, code) => post(`/pay/${payNo}/jsapi`, { code }),
  status: (payNo) => get(`/pay/${payNo}/status`, null, { silent: true })
};

export const walletApi = {
  wallet: () => get('/wallet'),
  logs: (params) => get('/wallet/logs', params)
};

export const withdrawApi = {
  config: () => get('/withdraws/config'),
  apply: (amount) => post('/withdraws', { amount }),
  page: (params) => get('/withdraws', params)
};

export const teamApi = {
  children: (parentId) => get('/team', parentId ? { parentId } : undefined)
};

export const performanceApi = {
  get: (month) => get('/performance', month ? { month } : undefined)
};

export const timelineApi = {
  page: (params) => get('/events', params)
};

export const noticeApi = {
  page: (params) => get('/notices', params),
  unreadCount: () => get('/notices/unread-count'),
  markRead: (id) => post(`/notices/${id}/read`)
};
