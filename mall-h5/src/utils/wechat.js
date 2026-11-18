const FORCE_WECHAT_KEY = 'mall_h5_force_wechat';
const BYPASS_KEY = 'mall_h5_bypass';

/**
 * 仅开发环境支持 ?wx=1 模拟微信内置浏览器，便于本地联调；生产构建不生效。
 */
function readDevMock() {
  if (!import.meta.env.DEV) return false;
  const hash = window.location.hash || '';
  const hashQuery = hash.includes('?') ? hash.slice(hash.indexOf('?')) : '';
  const isMock = new URLSearchParams(window.location.search).get('wx') === '1'
    || new URLSearchParams(hashQuery).get('wx') === '1';
  if (isMock) {
    sessionStorage.setItem(FORCE_WECHAT_KEY, '1');
  }
  return sessionStorage.getItem(FORCE_WECHAT_KEY) === '1';
}

const forced = readDevMock();

export function isWechat() {
  return forced || /micromessenger/i.test(navigator.userAgent);
}

/**
 * 手机微信内才允许支付；Windows/Mac 微信桌面端视为非手机环境。
 */
export function isMobileWechat() {
  if (forced) return true;
  const ua = navigator.userAgent;
  if (!/micromessenger/i.test(ua)) return false;
  const desktop = /Windows|Macintosh|Linux/i.test(ua) && !/Android|iPhone|iPad|iPod/i.test(ua);
  return !desktop;
}

export function hasBypass() {
  return sessionStorage.getItem(BYPASS_KEY) === '1';
}

export function setBypass() {
  sessionStorage.setItem(BYPASS_KEY, '1');
}

export function isDesktopWechat() {
  return isWechat() && !isMobileWechat();
}
