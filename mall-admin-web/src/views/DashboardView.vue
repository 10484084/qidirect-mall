<script setup>
import { onMounted, ref } from 'vue';
import { dashboardApi } from '../api';
import { fen2yuan } from '../utils/format';

const loading = ref(false);
const data = ref({});

async function load() {
  loading.value = true;
  try {
    data.value = (await dashboardApi.summary()) || {};
  } finally {
    loading.value = false;
  }
}

onMounted(load);

const groups = [
  {
    title: '今日数据',
    items: [
      { key: 'todayJoinCount', label: '加盟门店数', unit: '家' },
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
      { key: 'monthJoinCount', label: '加盟门店数', unit: '家' },
      { key: 'monthJoinAmount', label: '加盟金额', money: true },
      { key: 'monthRechargeCount', label: '预存笔数', unit: '笔' },
      { key: 'monthRechargeAmount', label: '预存金额', money: true },
      { key: 'monthOrderCount', label: '订单数', unit: '笔' },
      { key: 'monthOrderAmount', label: '订单金额', money: true }
    ]
  }
];

const pendingItems = [
  { key: 'pendingShipCount', label: '待发货订单', path: '/trade/orders?status=1', type: 'primary' },
  { key: 'pendingWithdrawCount', label: '待处理提现', path: '/withdraw/audit', type: 'warning' },
  { key: 'pendingAbnormalCount', label: '待处置异常单', path: '/finance/abnormal', type: 'danger' }
];

function display(item) {
  const v = data.value[item.key];
  if (v === null || v === undefined) return '-';
  return item.money ? `¥ ${fen2yuan(v)}` : v;
}
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-header">
      <h2 class="page-title">工作台</h2>
      <el-button :icon="'Refresh'" @click="load">刷新</el-button>
    </div>

    <el-row :gutter="16">
      <el-col v-for="p in pendingItems" :key="p.key" :span="8">
        <el-card shadow="hover" class="pending-card" @click="$router.push(p.path)">
          <div class="pending-label">{{ p.label }}</div>
          <el-badge :value="data[p.key] || 0" :type="p.type" :max="999">
            <div class="pending-value">{{ data[p.key] || 0 }}</div>
          </el-badge>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="mt16" shadow="never">
      <div class="perf">
        <span class="text-muted">本月业绩总额</span>
        <span class="perf-value">¥ {{ fen2yuan(data.monthPerformanceAmount) }}</span>
      </div>
    </el-card>

    <el-card v-for="g in groups" :key="g.title" class="mt16" shadow="never">
      <template #header>
        <span class="group-title">{{ g.title }}</span>
      </template>
      <el-row :gutter="16">
        <el-col v-for="item in g.items" :key="item.key" :span="4">
          <div class="stat">
            <div class="stat-value">{{ display(item) }}</div>
            <div class="stat-label text-muted">{{ item.label }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<style scoped>
.pending-card {
  cursor: pointer;
}

.pending-label {
  color: #606266;
  margin-bottom: 8px;
}

.pending-value {
  font-size: 26px;
  font-weight: 600;
}

.perf {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.perf-value {
  font-size: 24px;
  font-weight: 600;
  color: #f56c6c;
}

.group-title {
  font-weight: 600;
}

.stat {
  padding: 8px 0;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.stat-label {
  font-size: 12px;
  margin-top: 4px;
}
</style>
