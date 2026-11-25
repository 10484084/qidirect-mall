<template>
  <div class="page pay-page">
    <PageHeader title="支付" :back="false" />

    <div class="page-body">
      <div class="card pay-card">
        <template v-if="state === 'paying'">
          <template v-if="qrCode">
            <div class="pay-title">扫码支付</div>
            <div class="pay-amount">¥{{ fenToYuan(amount) }}</div>
            <img :src="qrCode" alt="支付二维码" class="qr-img" />
            <div class="muted small qr-tip">长按二维码识别，或截图后用微信/支付宝扫码支付</div>
          </template>
          <template v-else>
            <van-icon name="balance-pay" size="46" color="#326940" />
            <div class="pay-title">{{ jumped ? '等待支付结果' : '正在支付' }}</div>
            <div class="muted small">应付金额 ¥{{ fenToYuan(amount) }}</div>
          </template>
          <div class="pay-wait">
            <van-loading size="14" />
            <span>支付完成后自动返回</span>
          </div>
        </template>

        <template v-else-if="state === 'success'">
          <van-icon name="checked" size="52" color="#07c160" />
          <div class="pay-title">支付成功</div>
          <div class="pay-amount">¥{{ fenToYuan(amount) }}</div>
          <div class="muted small">储值已到账，正在跳转…</div>
        </template>

        <template v-else>
          <van-icon name="clear" size="52" color="#ee0a24" />
          <div class="pay-title">支付未完成</div>
          <div class="muted small">{{ message }}</div>
        </template>
      </div>

      <div class="page-body inner">
        <van-button
          v-if="state === 'success'"
          type="primary"
          block
          round
          @click="goTarget"
        >
          {{ orderId ? '查看订单' : '查看钱包' }}
        </van-button>
        <van-button v-else-if="state === 'fail'" type="primary" block round @click="retry">
          重新支付
        </van-button>
        <van-button plain block round class="mt-12" @click="$router.replace('/')">返回首页</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import axios from 'axios';
import PageHeader from '../components/PageHeader.vue';
import { payApi } from '../api';
import { fenToYuan } from '../utils/format';
import { assertPayAllowed, invokeWeixinBridge, mockPayUrl } from '../utils/pay';

const route = useRoute();
const router = useRouter();
const payNo = route.params.payNo;
const orderId = route.query.orderId ? Number(route.query.orderId) : null;
const amount = Number(route.query.amount || 0);
const state = ref('paying');
const qrCode = ref('');
const message = ref('支付超时，请重新发起支付');
const jumped = ref(false);
let timer = null;
let deadline = 0;
const jumpFlag = `payJump:${payNo}`;

onMounted(() => {
  if (!assertPayAllowed()) {
    state.value = 'fail';
    message.value = '当前环境不支持微信支付';
    return;
  }
  // 从扫呗收银台返回（历史返回/bfcache 恢复）：不再重复跳转，直接轮询确认
  if (sessionStorage.getItem(jumpFlag)) {
    jumped.value = true;
    poll(5 * 60 * 1000);
    return;
  }
  start();
});

// 微信浏览器 bfcache 恢复页面时定时器可能被冻结，回到前台立即重新轮询
window.addEventListener('pageshow', handleShow);
document.addEventListener('visibilitychange', handleShow);

function handleShow() {
  if (state.value !== 'paying' || !timer) return;
  if (!document.hidden) poll(jumped.value ? 5 * 60 * 1000 : 90 * 1000);
}

onBeforeUnmount(() => {
  stopPolling();
  window.removeEventListener('pageshow', handleShow);
  document.removeEventListener('visibilitychange', handleShow);
  sessionStorage.removeItem(jumpFlag);
});

function stopPolling() {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
}

async function start() {
  try {
    const info = await payApi.authorize(payNo);
    if (info.paid) {
      success();
      return;
    }
    if (info.payParams && info.payParams.package) {
      invokeWeixinBridge(info.payParams);
      poll();
      return;
    }
    if (info.qrCode) {
      // 微信内跳外部收银台会把 H5 顶掉，改为页内展示二维码，长按识别支付
      qrCode.value = info.qrCode;
      poll(5 * 60 * 1000);
      return;
    }
    if (info.payUrl) {
      if (mockPayUrl(info.payUrl)) {
        await axios.get(info.payUrl);
        success();
        return;
      }
      sessionStorage.setItem(jumpFlag, '1');
      jumped.value = true;
      window.location.href = info.payUrl;
      return;
    }
    if (info.authUrl) {
      window.location.href = info.authUrl;
      return;
    }
    poll();
  } catch (e) {
    state.value = 'fail';
    message.value = e.message || '支付发起失败';
  }
}

function poll(totalMs = 90 * 1000) {
  stopPolling();
  deadline = Date.now() + totalMs;
  timer = setInterval(async () => {
    if (Date.now() > deadline) {
      stopPolling();
      state.value = 'fail';
      message.value = '支付结果确认超时，请稍后在订单中查看或重新支付';
      return;
    }
    try {
      const result = await payApi.status(payNo);
      if (result.paid) {
        success();
      } else if (result.status === 4 || result.status === 5) {
        stopPolling();
        state.value = 'fail';
        message.value = '支付单已关闭，请重新下单';
      }
    } catch (e) {
      // 轮询失败继续
    }
  }, jumped.value ? 3000 : 1500);
}

function success() {
  stopPolling();
  sessionStorage.removeItem(jumpFlag);
  state.value = 'success';
  // 支付成功后自动回到商城，无需用户再点按钮
  setTimeout(() => {
    if (state.value === 'success') goTarget();
  }, 1800);
}

function goTarget() {
  if (orderId) router.replace(`/orders/${orderId}`);
  else router.replace('/wallet');
}

function retry() {
  state.value = 'paying';
  message.value = '';
  start();
}
</script>

<style scoped>
.pay-page {
  padding-bottom: 20px;
}

.pay-card {
  text-align: center;
  padding: 36px 20px;
}

.pay-wait {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 14px;
  font-size: 13px;
  color: var(--text-sub);
}

.pay-wait {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 14px;
  font-size: 13px;
  color: var(--text-sub);
}

.qr-img {
  width: 200px;
  height: 200px;
  margin: 10px auto 6px;
  display: block;
  border-radius: 8px;
  background: #fff;
}

.qr-tip {
  line-height: 1.5;
  padding: 0 8px;
}

.pay-amount {
  margin: 4px 0 6px;
  font-size: 24px;
  font-weight: 600;
  color: #326940;
}

.pay-title {
  font-size: 18px;
  font-weight: 600;
  margin: 14px 0 8px;
}

.inner {
  padding: 0;
}
</style>
