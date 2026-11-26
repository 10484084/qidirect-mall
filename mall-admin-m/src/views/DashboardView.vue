<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { dashboardApi } from '../api';
import { fen2yuan } from '../utils/format';
import { useUserStore } from '../stores/user';

defineOptions({ name: 'DashboardView' });

const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const refreshing = ref(false);
const data = ref({});

async function load() {
  loading.value = true;
  try {
    data.value = (await dashboardApi.summary()) || {};
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

const pending = [
  { key: 'pendingShipCount', label: '待发货订单', to: '/orders?status=1' },
  { key: 'pendingWithdrawCount', label: '待处理提现', to: '/withdraws?status=0' }
];

const groups = [
  {
    title: '今日数据',
    items: [
      { key: 'todayJoinCount', label: '加盟门店', unit: '家' },
      { key: 'todayJoinAmount', label: '加盟金额', money: true },
      { key: 'todayRechargeCount', label: '预存笔数', unit: '笔' },
      { key: 'todayRechargeAmount', label: '预存金额', money: true },
      { key: 'todayOrderCount', label: '订单数', unit: '笔' },
      { key: 'todayOrderAmount', label: '订单金额', money: true }
    ]
  },
  {
    title: '本月数据',
    items: [
      { key: 'monthJoinCount', label: '加盟门店', unit: '家' },
      { key: 'monthJoinAmount', label: '加盟金额', money: true },
      { key: 'monthRechargeCount', label: '预存笔数', unit: '笔' },
      { key: 'monthRechargeAmount', label: '预存金额', money: true },
      { key: 'monthOrderCount', label: '订单数', unit: '笔' },
      { key: 'monthOrderAmount', label: '订单金额', money: true }
    ]
  }
];

function display(item) {
  const v = data.value[item.key];
  if (v === null || v === undefined) return '-';
  return item.money ? `¥${fen2yuan(v)}` : v;
}

const today = new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' });

onMounted(load);
</script>

<template>
  <van-pull-refresh v-model="refreshing" @refresh="load">
    <div class="page page-plain">
      <header class="hero">
        <div>
          <div class="hello">你好，{{ userStore.realName || '管理员' }}</div>
          <div class="date">{{ today }}</div>
        </div>
        <van-icon name="replay" size="20" class="refresh" :class="{ spin: loading }" @click="load" />
      </header>

      <div class="pending">
        <div v-for="p in pending" :key="p.key" class="pending-card" @click="router.push(p.to)">
          <div class="pending-value">{{ data[p.key] || 0 }}</div>
          <div class="pending-label">{{ p.label }}</div>
        </div>
        <div class="pending-card money-card">
          <div class="pending-value">¥{{ fen2yuan(data.monthPerformanceAmount) }}</div>
          <div class="pending-label">本月业绩</div>
        </div>
      </div>

      <div v-for="g in groups" :key="g.title">
        <div class="section-title">{{ g.title }}</div>
        <div class="card grid-card">
          <div v-for="item in g.items" :key="item.key" class="grid-item">
            <div class="grid-value" :class="{ money: item.money }">{{ display(item) }}</div>
            <div class="grid-label">{{ item.label }}</div>
          </div>
        </div>
      </div>

      <p class="tip">下拉可刷新数据 · 异常单处置请使用电脑版后台</p>
    </div>
  </van-pull-refresh>
</template>

<style scoped>
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 4px 14px;
}

.hello {
  font-size: 19px;
  font-weight: 600;
}

.date {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-sub);
}

.refresh {
  color: var(--text-sub);
  padding: 6px;
}

.spin {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.pending {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.pending-card {
  background: linear-gradient(160deg, #ffffff, #eff4ff);
  border-radius: var(--card-radius);
  padding: 14px 10px;
  text-align: center;
  box-shadow: 0 1px 2px rgba(31, 35, 41, 0.05);
}

.pending-card:active {
  opacity: 0.8;
}

.money-card {
  background: linear-gradient(160deg, #fff6f4, #ffece8);
}

.pending-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--brand);
  font-variant-numeric: tabular-nums;
}

.money-card .pending-value {
  font-size: 16px;
  color: var(--money);
}

.pending-label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-sub);
}

.grid-card {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 4px 0;
  padding: 6px 0;
}

.grid-item {
  padding: 10px 14px;
}

.grid-value {
  font-size: 17px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.grid-label {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-sub);
}

.tip {
  margin: 18px 4px 0;
  text-align: center;
  font-size: 12px;
  color: #b7bcc4;
}
</style>
