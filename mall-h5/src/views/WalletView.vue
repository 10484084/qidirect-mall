<template>
  <div class="page wallet-page">
    <PageHeader title="我的钱包" />

    <div class="wallet-hero">
      <div class="hero-cols">
        <div class="hero-col">
          <div class="hero-num">{{ fenToYuan(wallet.depositBalance) }}</div>
          <div class="hero-label">预存账户</div>
        </div>
        <div class="hero-col">
          <div class="hero-num">{{ fenToYuan(wallet.commissionBalance) }}</div>
          <div class="hero-label">佣金账户</div>
        </div>
        <div class="hero-col">
          <div class="hero-num">{{ fenToYuan(wallet.availableCommission) }}</div>
          <div class="hero-label">可提金额</div>
        </div>
      </div>
      <div class="hero-foot">
        <span class="hero-tip">冻结 {{ fenToYuan(wallet.frozenAmount) }} · 预存仅可下单抵扣</span>
        <van-button size="mini" round class="withdraw-btn" @click="$router.push('/withdraw')">申请提现</van-button>
      </div>
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
  background: linear-gradient(150deg, #3d7a4c, #245234);
  color: #fff;
  position: relative;
}

.hero-cols {
  display: flex;
  gap: 6px;
}

.hero-col {
  flex: 1;
  min-width: 0;
}

.hero-num {
  font-size: 22px;
  font-weight: 600;
  color: #fff;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.hero-label {
  margin-top: 6px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.75);
}

.hero-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
}

.hero-tip {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.62);
}

.withdraw-btn {
  color: #326940;
  border: none;
  flex-shrink: 0;
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
