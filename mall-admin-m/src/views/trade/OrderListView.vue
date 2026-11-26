<script setup>
import { onActivated, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { orderApi } from '../../api';
import { ORDER_STATUS } from '../../utils/dict';
import { fen2yuan } from '../../utils/format';

defineOptions({ name: 'OrderListView' });

const route = useRoute();
const router = useRouter();

const TABS = [
  { name: 'all', title: '全部' },
  { name: '0', title: '待支付' },
  { name: '1', title: '待发货' },
  { name: '2', title: '已发货' },
  { name: '3', title: '已完成' },
  { name: '4', title: '已取消' }
];

const initStatus = route.query.status !== undefined && route.query.status !== '' ? String(route.query.status) : 'all';
const activeTab = ref(TABS.some((t) => t.name === initStatus) ? initStatus : 'all');
const keyword = ref('');
const searchText = ref('');

const orders = ref([]);
const page = ref(1);
const size = 20;
const loading = ref(false);
const finished = ref(false);
const refreshing = ref(false);

function statusTag(status) {
  const meta = ORDER_STATUS[status] || { label: '-', type: 'info' };
  const cls = { warning: 'tag-warn', primary: '', success: 'tag-success', danger: 'tag-danger', info: 'tag-gray' };
  return { label: meta.label, cls: cls[meta.type] ?? 'tag-gray' };
}

async function fetchPage() {
  const params = { page: page.value, size };
  if (activeTab.value !== 'all') params.status = Number(activeTab.value);
  if (keyword.value) params.orderNo = keyword.value;
  const res = await orderApi.page(params);
  const records = (res && res.records) || [];
  orders.value = page.value === 1 ? records : orders.value.concat(records);
  const total = (res && res.total) || 0;
  if (orders.value.length >= total || records.length === 0) {
    finished.value = true;
  }
  page.value += 1;
}

async function resetList() {
  page.value = 1;
  finished.value = false;
  orders.value = [];
  loading.value = true;
  try {
    await fetchPage();
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

async function onLoad() {
  if (finished.value) {
    loading.value = false;
    return;
  }
  try {
    await fetchPage();
  } finally {
    loading.value = false;
  }
}

function onTabChange() {
  resetList();
}

function onSearch() {
  keyword.value = searchText.value.trim();
  resetList();
}

function onClear() {
  searchText.value = '';
  if (keyword.value) {
    keyword.value = '';
    resetList();
  }
}

onMounted(resetList);

let firstActivate = true;
onActivated(() => {
  // keep-alive 首次激活与 onMounted 重叠，跳过；从详情返回时刷新首屏
  if (firstActivate) {
    firstActivate = false;
    return;
  }
  resetList();
});
</script>

<template>
  <div class="page">
    <div class="sticky-bar">
      <van-search
        v-model="searchText"
        placeholder="搜索订单号"
        shape="round"
        background="#fff"
        @search="onSearch"
        @clear="onClear"
      />
      <van-tabs v-model:active="activeTab" color="#326940" title-active-color="#326940" @change="onTabChange">
        <van-tab v-for="t in TABS" :key="t.name" :name="t.name" :title="t.title" />
      </van-tabs>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="resetList">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        :immediate-check="false"
        @load="onLoad"
      >
        <div
          v-for="order in orders"
          :key="order.id"
          class="card"
          @click="router.push(`/orders/${order.id}`)"
        >
          <div class="row">
            <span class="order-no ellipsis">{{ order.orderNo }}</span>
            <span class="tag-text" :class="statusTag(order.status).cls">{{ statusTag(order.status).label }}</span>
          </div>

          <div class="row store-line">
            <span class="strong ellipsis">{{ order.storeName || '-' }}</span>
            <span class="muted">{{ order.storePhone }}</span>
          </div>

          <div class="row">
            <span class="muted ellipsis">
              {{ order.receiverName }} {{ order.receiverPhone }} · {{ order.receiverAddr }}
            </span>
          </div>

          <div class="row foot">
            <span class="muted">{{ order.createdAt }}</span>
            <span class="money big">¥{{ fen2yuan(order.totalAmount) }}</span>
          </div>

          <div class="row">
            <span class="muted">预存抵扣 ¥{{ fen2yuan(order.depositAmount) }}</span>
            <span v-if="order.status === 1" class="ship-hint">待发货，点击去发货</span>
          </div>
        </div>

        <div v-if="!loading && !orders.length" class="empty-tip">暂无订单</div>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.sticky-bar {
  margin: -12px -12px 8px;
  padding-bottom: 0;
}

.sticky-bar :deep(.van-search) {
  padding-bottom: 6px;
}

.order-no {
  font-size: 12px;
  color: var(--text-sub);
  font-variant-numeric: tabular-nums;
  max-width: 62%;
}

.store-line {
  margin-top: 8px;
  font-size: 15px;
}

.foot {
  margin-top: 8px;
}

.money.big {
  font-size: 16px;
}

.ship-hint {
  font-size: 12px;
  color: var(--brand);
}
</style>
