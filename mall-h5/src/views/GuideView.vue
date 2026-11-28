<template>
  <div class="guide">
    <div class="logo">店</div>
    <h1>请在微信中打开</h1>
    <p class="desc">
      {{ isDesktopWechat() ? '当前为微信电脑版，请使用手机微信扫码或打开链接，以获得完整的下单与支付体验。' : '门店下单、支付与提现均需在微信内置浏览器中完成，请使用手机微信打开本页面。' }}
    </p>

    <div class="link-box">
      <span class="ellipsis">{{ pageUrl }}</span>
      <van-button size="small" type="primary" plain @click="copyLink">复制链接</van-button>
    </div>

    <div class="actions">
      <van-button type="primary" block round @click="copyLink">复制链接到微信打开</van-button>
      <van-button plain block round class="mt-12" @click="continueBrowse">仅浏览（无法支付）</van-button>
    </div>

    <p class="tips">提示：在电脑浏览器中可浏览开通与商品信息，但支付环节会被拦截。</p>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import { isDesktopWechat, setBypass } from '../utils/wechat';

const route = useRoute();
const router = useRouter();
const pageUrl = window.location.href;

async function copyLink() {
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(pageUrl);
    } else {
      const input = document.createElement('textarea');
      input.value = pageUrl;
      document.body.appendChild(input);
      input.select();
      document.execCommand('copy');
      document.body.removeChild(input);
    }
    showToast('链接已复制，请到微信中粘贴打开');
  } catch (e) {
    showToast('复制失败，请长按地址栏手动复制');
  }
}

function continueBrowse() {
  setBypass();
  const redirect = route.query.redirect;
  router.replace(typeof redirect === 'string' ? redirect : '/');
}
</script>

<style scoped>
.guide {
  min-height: 100vh;
  padding: 64px 28px 32px;
  background: linear-gradient(160deg, #3d7a4c 0%, #2b5c38 55%, #1e4426 100%);
  color: #fff;
  text-align: center;
}

.logo {
  width: 76px;
  height: 76px;
  border-radius: 22px;
  margin: 0 auto 20px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.35);
  font-size: 34px;
  line-height: 74px;
}

h1 {
  font-size: 22px;
  margin: 0 0 12px;
}

.desc {
  font-size: 14px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.82);
  margin: 0 auto 26px;
  max-width: 320px;
}

.link-box {
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 10px;
  padding: 8px 12px;
  text-align: left;
  font-size: 12px;
  margin-bottom: 28px;
}

.link-box span {
  flex: 1;
}

.actions {
  margin-bottom: 18px;
}

.tips {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
  line-height: 1.6;
}
</style>
