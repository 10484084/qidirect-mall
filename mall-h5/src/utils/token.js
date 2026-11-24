const TOKEN_KEY = 'mall_h5_token';
const PWD_FLAG_KEY = 'mall_h5_pwd_need_change';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || '';
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token || '');
}

/**
 * 门店是否仍在使用初始密码（后台创建/重置后为 true），需强制改密。
 */
export function needChangePwd() {
  return localStorage.getItem(PWD_FLAG_KEY) === '1';
}

export function setNeedChangePwd(flag) {
  if (flag) {
    localStorage.setItem(PWD_FLAG_KEY, '1');
  } else {
    localStorage.removeItem(PWD_FLAG_KEY);
  }
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(PWD_FLAG_KEY);
}

/**
 * 本地解析 JWT 过期时间，提前跳登录，避免请求后才 401。
 */
export function isTokenExpired(token = getToken()) {
  if (!token) return true;
  const parts = token.split('.');
  if (parts.length !== 3) return true;
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
    const payload = JSON.parse(new TextDecoder().decode(bytes));
    if (!payload.exp) return false;
    return payload.exp * 1000 <= Date.now();
  } catch (e) {
    return false;
  }
}
