<template>
  <div class="page with-tab orders-page">
    <van-tabs v-model:active="status" sticky @change="reload">
      <van-tab title="全部" :name="-1" />
      <van-tab title="待支付" :name="0" />
      <van-tab title="待发货" :name="1" />
      <van-tab title="已发货" :name="2" />
      <van-tab title="已完成" :name="3" />
      <van-tab title="已取消" :name="4" />
    </van-tabs>

    <div class="page-body">
      <van-pull-refresh v-model="refreshing" @refresh="reload">
        <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
          <div v-for="order in orders" :key="order.id" class="card order-card" @click="$router.push(`/orders/${order.id}`)">
            <div class="flex-between">
              <span class="muted small ellipsis">{{ order.orderNo }}</span>
              <van-tag :type="orderStatusType(order.status)">{{ orderStatusText(order.status) }}</van-tag>
            </div>
            <div class="items">
              <div v-for="(item, index) in (order.items || []).slice(0, 3)" :key="index" class="item-line">
                <span class="ellipsis">{{ item.productName }} · {{ item.specName }} × {{ item.qty }}</span>
                <span v-if="item.isGift === 1" class="tag-gift">赠品</span>
              </div>
              <div v-if="(order.items || []).length > 3" class="muted small">等 {{ order.items.length }} 项商品</div>
            </div>
            <div class="flex-between mt-8">
              <span class="muted small">{{ formatTime(order.createdAt) }}</span>
              <span>合计 <span class="money">{{ fenToYuan(order.totalAmount) }}</span></span>
            </div>
            <div class="actions" @click.stop>
              <van-button v-if="order.status === 0 && order.payNo" size="small" type="primary" round @click="goPay(order)">去支付</van-button>
              <van-button v-if="order.status === 0" size="small" plain round @click="cancel(order)">取消订单</van-button>
              <van-button v-if="order.status === 2" size="small" type="primary" round @click="confirm(order)">确认收货</van-button>
            </div>
          </div>
          <div v-if="!orders.length && finished" class="text-empty">暂无订单</div>
        </van-list>
      </van-pull-refresh>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showConfirmDialog, showToast } from 'vant';
import { orderApi } from '../api';
import { fenToYuan, formatTime, orderStatusText, orderStatusType } from '../utils/format';
import { useCartStore } from '../stores/cart';

const router = useRouter();
const cart = useCartStore();
const status = ref(-1);
const orders = ref([]);
const page = ref(1);
const size = 10;
const loading = ref(false);
const finished = ref(false);
const refreshing = ref(false);

async function loadMore() {
  loading.value = true;
  try {
    const result = await orderApi.page({
      status: status.value >= 0 ? status.value : undefined,
      page: page.value,
      size
    });
    const records = result.records || [];
    orders.value = page.value === 1 ? records : orders.value.concat(records);
    finished.value = orders.value.length >= result.total;
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
  orders.value = [];
  loadMore().finally(() => {
    refreshing.value = false;
  });
}

function goPay(order) {
  router.push({ path: `/pay/${order.payNo}`, query: { orderId: order.id, amount: order.onlineAmount } });
}

async function cancel(order) {
  try {
    await showConfirmDialog({ title: '取消订单', message: '取消后将退回已抵扣的预存余额，确定取消吗？' });
  } catch (e) {
    return;
  }
  try {
    await orderApi.cancel(order.id);
    showToast('订单已取消');
    reload();
  } catch (e) {
    // 错误已提示
  }
}

async function confirm(order) {
  try {
    await showConfirmDialog({ title: '确认收货', message: '请确认已收到全部商品' });
  } catch (e) {
    return;
  }
  try {
    await orderApi.confirm(order.id);
    showToast('已确认收货');
    reload();
  } catch (e) {
    // 错误已提示
  }
}

onMounted(() => {
  cart.refresh().catch(() => {});
});
</script>

<style scoped>
.orders-page {
  padding-bottom: 70px;
}

.order-card {
  margin-bottom: 10px;
}

.items {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #f4f5f7;
}

.item-line {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  padding: 3px 0;
}

.item-line span:first-child {
  flex: 1;
  min-width: 0;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}
</style>
