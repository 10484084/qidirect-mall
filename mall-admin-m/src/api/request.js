import axios from 'axios';
import { showFailToast } from 'vant';
import { clearToken, getToken, isTokenExpired } from '../utils/token';

const instance = axios.create({
  baseURL: '/admin',
  timeout: 20000
});

let redirecting = false;

function toLogin() {
  if (redirecting) return;
  redirecting = true;
  clearToken();
  const current = window.location.hash.replace(/^#/, '') || '/';
  window.location.hash = `#/login?redirect=${encodeURIComponent(current)}`;
  setTimeout(() => {
    redirecting = false;
  }, 300);
}

instance.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    if (isTokenExpired(token)) {
      toLogin();
      return Promise.reject(new Error('登录已过期'));
    }
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

instance.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response;
    }
    const body = response.data;
    if (!body || typeof body !== 'object' || !('code' in body)) {
      return body;
    }
    if (body.code === 0) {
      return body.data;
    }
    if (!response.config.silent) {
      showFailToast(body.msg || '请求失败');
    }
    if (body.code === 10002) {
      toLogin();
    }
    return Promise.reject(new Error(body.msg || '请求失败'));
  },
  (error) => {
    const status = error.response && error.response.status;
    if (status === 401) {
      toLogin();
    } else if (!error.config?.silent) {
      showFailToast(error.response?.data?.msg || error.message || '网络异常');
    }
    return Promise.reject(error);
  }
);

export const get = (url, params, config = {}) => instance.get(url, { params, ...config });
export const post = (url, data, config = {}) => instance.post(url, data, config);
export const put = (url, data, config = {}) => instance.put(url, data, config);
export const del = (url, config = {}) => instance.delete(url, config);

/**
 * 上传图片，返回可访问地址（如 /files/20260101/xxx.png）。
 */
export async function uploadFile(file) {
  const form = new FormData();
  form.append('file', file);
  return instance.post('/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } });
}

export default instance;
