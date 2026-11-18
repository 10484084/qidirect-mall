<template>
  <div class="page pay-page">
    <PageHeader title="支付" :back="false" />

    <div class="page-body">
      <div class="card pay-card">
        <template v-if="state === 'paying'">
          <van-icon name="balance-pay" size="46" color="#1f6feb" />
          <div class="pay-title">正在支付</div>
          <div class="muted small">应付金额 ¥{{ fenToYuan(amount) }}</div>
          <van-loading class="mt-12" size="20">等待支付结果...</van-loading>
        </template>

        <template v-else-if="state === 'success'">
          <van-icon name="checked" size="52" color="#07c160" />
          <div class="pay-title">支付成功</div>
          <div class="muted small">业务已生效，可在记录中查看明细</div>
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
          {{ orderId ? '查看订单' : '查看记录' }}
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
const message = ref('支付超时，请重新发起支付');
let timer = null;
let deadline = 0;

onMounted(() => {
  if (!assertPayAllowed()) {
    state.value = 'fail';
    message.value = '当前环境不支持微信支付';
    return;
  }
  start();
});

onBeforeUnmount(stopPolling);

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
    if (info.payUrl) {
      if (mockPayUrl(info.payUrl)) {
        await axios.get(info.payUrl);
        success();
        return;
      }
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

function poll() {
  stopPolling();
  deadline = Date.now() + 90 * 1000;
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
  }, 1500);
}

function success() {
  stopPolling();
  state.value = 'success';
}

function goTarget() {
  if (orderId) router.replace(`/orders/${orderId}`);
  else router.replace('/timeline');
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

.pay-title {
  font-size: 18px;
  font-weight: 600;
  margin: 14px 0 8px;
}

.inner {
  padding: 0;
}
</style>
