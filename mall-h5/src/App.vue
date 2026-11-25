<template>
  <router-view />
  <TabBar v-if="showTab" />
</template>

<script setup>
import { computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import TabBar from './components/TabBar.vue';
import { useUserStore } from './stores/user';
import { payApi } from './api';
import { getToken } from './utils/token';

const PENDING_PAY_KEY = 'pending_pay';
/** 待确认支付的有效期：与支付单超时（默认 10 分钟）对齐 */
const PENDING_TTL = 15 * 60 * 1000;

const route = useRoute();
const router = useRouter();
const user = useUserStore();
const showTab = computed(() => Boolean(route.meta.tab));

/**
 * 微信内跳收银台后没有返回按钮，付完往往直接关窗回聊天。
 * 用户下次打开商城时，用这里补一次查单：已支付就带到订单页/钱包，未支付静默清理。
 */
async function resumePendingPay() {
  const raw = localStorage.getItem(PENDING_PAY_KEY);
  if (!raw || !getToken()) {
    return;
  }
  let pending;
  try {
    pending = JSON.parse(raw);
  } catch (e) {
    localStorage.removeItem(PENDING_PAY_KEY);
    return;
  }
  if (!pending || !pending.payNo || Date.now() - (pending.at || 0) > PENDING_TTL) {
    localStorage.removeItem(PENDING_PAY_KEY);
    return;
  }
  if (route.path.startsWith('/pay/')) {
    return;
  }
  try {
    const status = await payApi.status(pending.payNo);
    if (status && status.paid) {
      localStorage.removeItem(PENDING_PAY_KEY);
      showToast('支付已完成');
      router.replace(pending.orderId ? `/orders/${pending.orderId}` : '/wallet');
      return;
    }
    if (status && (status.status === 4 || status.status === 5)) {
      localStorage.removeItem(PENDING_PAY_KEY);
    }
  } catch (e) {
    // 查单失败不影响正常使用，保留标记供下次再试
  }
}

onMounted(() => {
  if (getToken()) {
    user.loadProfile().catch(() => {});
    resumePendingPay();
  }
});
</script>
