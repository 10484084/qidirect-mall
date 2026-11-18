const TOKEN_KEY = 'mall_h5_token';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || '';
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token || '');
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
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
