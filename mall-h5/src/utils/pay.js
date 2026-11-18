import { showDialog, showToast } from 'vant';
import { isMobileWechat } from './wechat';

/**
 * 支付环节二次拦截：非手机微信环境直接阻断，不发起任何支付请求。
 */
export function assertPayAllowed() {
  if (isMobileWechat()) return true;
  showDialog({
    title: '请在微信中支付',
    message: '当前环境不支持微信支付，请使用手机微信打开本页面后再完成支付。',
    confirmButtonText: '我知道了'
  });
  return false;
}

export function mockPayUrl(payUrl) {
  return payUrl && payUrl.startsWith('/api/notify/mock/');
}

export function invokeWeixinBridge(payParams) {
  if (!payParams || !payParams.package) return false;
  const invoke = () => {
    if (typeof window.WeixinJSBridge === 'undefined') return false;
    window.WeixinJSBridge.invoke('getBrandWCPayRequest', payParams, () => {});
    return true;
  };
  if (!invoke()) {
    showToast('请在微信中打开以完成支付');
  }
  return true;
}
