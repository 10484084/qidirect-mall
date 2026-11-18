import axios from 'axios';
import { showToast } from 'vant';
import { clearToken, getToken, isTokenExpired } from '../utils/token';

const instance = axios.create({
  baseURL: '/api',
  timeout: 20000
});

function redirectLogin() {
  clearToken();
  const current = window.location.hash || '';
  if (current.includes('/login')) return;
  const redirect = current.startsWith('#/') ? current.slice(1) : '/';
  window.location.hash = `/login?redirect=${encodeURIComponent(redirect)}`;
}

instance.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    if (isTokenExpired(token)) {
      redirectLogin();
      return Promise.reject(new Error('登录已过期，请重新登录'));
    }
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

instance.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (body && typeof body.code === 'number') {
      if (body.code === 0) return body.data;
      const error = new Error(body.msg || '请求失败');
      error.code = body.code;
      if (!response.config.silent) showToast(error.message);
      return Promise.reject(error);
    }
    return body;
  },
  (error) => {
    const status = error.response && error.response.status;
    const silent = error.config && error.config.silent;
    if (status === 401) {
      if (!silent) showToast('登录已过期，请重新登录');
      redirectLogin();
      return Promise.reject(new Error('登录已过期'));
    }
    const message = status === 403
      ? '没有访问权限'
      : (error.response && error.response.data && error.response.data.msg) || error.message || '网络异常';
    if (!silent) showToast(message);
    return Promise.reject(new Error(message));
  }
);

export function get(url, params, config = {}) {
  return instance.get(url, { params, ...config });
}

export function post(url, data, config = {}) {
  return instance.post(url, data, config);
}

export function put(url, data, config = {}) {
  return instance.put(url, data, config);
}

export function del(url, config = {}) {
  return instance.delete(url, config);
}

export default instance;
