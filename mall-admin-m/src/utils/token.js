/**
 * 移动端后台独立存储键，避免与桌面版（/admin-web/）在同域下互相覆盖登录态。
 */
const TOKEN_KEY = 'mall_admin_m_token';
const PROFILE_KEY = 'mall_admin_m_profile';
const ACCOUNT_KEY = 'mall_admin_m_account';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || '';
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function getProfileCache() {
  try {
    const raw = localStorage.getItem(PROFILE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    return null;
  }
}

export function setProfileCache(profile) {
  localStorage.setItem(PROFILE_KEY, JSON.stringify(profile));
}

export function getLastAccount() {
  return localStorage.getItem(ACCOUNT_KEY) || '';
}

export function setLastAccount(account) {
  localStorage.setItem(ACCOUNT_KEY, account || '');
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(PROFILE_KEY);
}

export function isTokenExpired(token) {
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
    return true;
  }
}
