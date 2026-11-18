<template>
  <div class="page wallet-page">
    <PageHeader title="我的钱包" />

    <div class="wallet-hero">
      <div class="hero-line">
        <span>预存余额</span>
        <span class="amount">{{ fenToYuan(wallet.depositBalance) }}</span>
      </div>
      <div class="hero-sub">
        <span>佣金余额 {{ fenToYuan(wallet.commissionBalance) }}</span>
        <span>冻结 {{ fenToYuan(wallet.frozenAmount) }}</span>
        <span>可提现 {{ fenToYuan(wallet.availableCommission) }}</span>
      </div>
      <van-button size="small" round class="withdraw-btn" @click="$router.push('/withdraw')">申请提现</van-button>
    </div>

    <van-tabs v-model:active="account" sticky @change="reload">
      <van-tab title="预存流水" :name="1" />
      <van-tab title="佣金流水" :name="2" />
    </van-tabs>

    <div class="page-body">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <div v-for="log in logs" :key="log.id" class="card log-card">
          <div class="flex-between">
            <span class="log-type">{{ bizTypeText(log.bizType) }}</span>
            <span :class="log.direction === 1 ? 'success' : 'danger'" class="log-amount">
              {{ log.direction === 1 ? '+' : '-' }}{{ fenToYuan(log.amount) }}
            </span>
          </div>
          <div class="muted small mt-8">{{ log.remark || log.bizNo || '-' }}</div>
          <div class="flex-between mt-8">
            <span class="muted small">{{ formatTime(log.createdAt) }}</span>
            <span class="muted small">余额 {{ fenToYuan(log.balanceAfter) }}</span>
          </div>
        </div>
        <div v-if="!logs.length && finished" class="text-empty">暂无流水</div>
      </van-list>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import PageHeader from '../components/PageHeader.vue';
import { walletApi } from '../api';
import { bizTypeText, fenToYuan, formatTime } from '../utils/format';

const wallet = ref({ depositBalance: 0, commissionBalance: 0, frozenAmount: 0, availableCommission: 0 });
const account = ref(1);
const logs = ref([]);
const page = ref(1);
const size = 20;
const loading = ref(false);
const finished = ref(false);

async function loadWallet() {
  wallet.value = await walletApi.wallet();
}

async function loadMore() {
  loading.value = true;
  try {
    const result = await walletApi.logs({ account: account.value, page: page.value, size });
    const records = result.records || [];
    logs.value = page.value === 1 ? records : logs.value.concat(records);
    finished.value = logs.value.length >= result.total;
    if (!finished.value) page.value += 1;
  } catch (e) {
    finished.value = true;
  } finally {
    loading.value = false;
  }
}

function reload() {
  page.value = 1;
  finished.value = false;
  logs.value = [];
  loadMore();
}

onMounted(() => {
  loadWallet().catch(() => {});
});
</script>

<style scoped>
.wallet-hero {
  margin: 12px;
  padding: 18px;
  border-radius: 14px;
  background: linear-gradient(150deg, #2b7cf5, #0f4fbf);
  color: #fff;
  position: relative;
}

.hero-line {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.8);
}

.amount {
  font-size: 28px;
  font-weight: 600;
  color: #fff;
}

.hero-sub {
  display: flex;
  gap: 14px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.75);
  margin-top: 8px;
}

.withdraw-btn {
  position: absolute;
  right: 16px;
  top: 18px;
  color: #0f4fbf;
  border: none;
}

.log-card {
  margin-bottom: 10px;
}

.log-type {
  font-size: 14px;
  font-weight: 500;
}

.log-amount {
  font-size: 16px;
  font-weight: 600;
}
</style>
