<template>
  <div class="page detail-page">
    <PageHeader title="订单详情" />

    <van-loading v-if="loading" class="loading" />
    <template v-else-if="order">
      <div class="status-card">
        <div class="status-text">{{ orderStatusText(order.status) }}</div>
        <div class="muted small">{{ statusHint }}</div>
      </div>

      <div class="page-body">
        <div class="card">
          <div class="card-title">商品清单</div>
          <div v-for="(item, index) in order.items" :key="index" class="line-item">
            <div class="line-info">
              <div>
                {{ item.productName }}
                <span v-if="item.isGift === 1" class="tag-gift">赠品</span>
              </div>
              <div class="muted small">{{ item.specName }} × {{ item.qty }}</div>
            </div>
            <span :class="{ money: item.isGift !== 1 }">{{ item.isGift === 1 ? '¥0.00' : `¥${fenToYuan(item.amount)}` }}</span>
          </div>
        </div>

        <div class="card">
          <div class="card-title">金额</div>
          <div class="amount-line"><span class="muted">商品合计</span><span>¥{{ fenToYuan(order.totalAmount) }}</span></div>
          <div class="amount-line"><span class="muted">预存抵扣</span><span>-¥{{ fenToYuan(order.depositAmount) }}</span></div>
          <div class="amount-line"><span class="muted">微信支付</span><span class="money">{{ fenToYuan(order.onlineAmount) }}</span></div>
        </div>

        <div class="card">
          <div class="card-title">收货与发货</div>
          <div class="kv"><span class="muted">收货人</span><span>{{ order.receiverName }} {{ order.receiverPhone }}</span></div>
          <div class="kv"><span class="muted">收货地址</span><span class="addr">{{ order.receiverAddr }}</span></div>
          <div v-if="order.shipType" class="kv"><span class="muted">发货方式</span><span>{{ order.shipType === 1 ? '物流发货' : '其他方式' }}</span></div>
          <div v-if="order.shipCompany" class="kv"><span class="muted">物流公司</span><span>{{ order.shipCompany }}</span></div>
          <div v-if="order.shipNo" class="kv"><span class="muted">物流单号</span><span>{{ order.shipNo }}</span></div>
          <div v-if="order.shipRemark" class="kv"><span class="muted">发货备注</span><span>{{ order.shipRemark }}</span></div>
          <div v-if="(order.shipImages || []).length" class="ship-images">
            <img v-for="(img, index) in order.shipImages" :key="index" :src="img" alt="发货凭证" />
          </div>
        </div>

        <div class="card">
          <div class="card-title">订单信息</div>
          <div class="kv"><span class="muted">订单号</span><span>{{ order.orderNo }}</span></div>
          <div class="kv"><span class="muted">下单时间</span><span>{{ formatTime(order.createdAt) }}</span></div>
          <div v-if="order.payTime" class="kv"><span class="muted">支付时间</span><span>{{ formatTime(order.payTime) }}</span></div>
          <div v-if="order.shipTime" class="kv"><span class="muted">发货时间</span><span>{{ formatTime(order.shipTime) }}</span></div>
          <div v-if="order.finishTime" class="kv"><span class="muted">完成时间</span><span>{{ formatTime(order.finishTime) }}</span></div>
        </div>
      </div>

      <div class="footer-bar">
        <van-button v-if="order.status === 0 && order.payNo" class="grow" type="primary" round @click="goPay">去支付</van-button>
        <van-button v-if="order.status === 0" class="grow" plain round @click="cancel">取消订单</van-button>
        <van-button v-if="order.status === 2" class="grow" type="primary" round @click="confirm">确认收货</van-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showConfirmDialog, showToast } from 'vant';
import PageHeader from '../components/PageHeader.vue';
import { orderApi } from '../api';
import { fenToYuan, formatTime, orderStatusText } from '../utils/format';

const route = useRoute();
const router = useRouter();
const order = ref(null);
const loading = ref(true);

const statusHint = computed(() => {
  if (!order.value) return '';
  return {
    0: '请在 10 分钟内完成支付，超时自动取消',
    1: '厂家正在备货，请耐心等待',
    2: '商品已发出，请注意查收',
    3: '订单已完成，感谢您的订购',
    4: '订单已取消，预存已退回'
  }[order.value.status] || '';
});

onMounted(load);

async function load() {
  loading.value = true;
  try {
    order.value = await orderApi.detail(route.params.id);
  } catch (e) {
    order.value = null;
  } finally {
    loading.value = false;
  }
}

function goPay() {
  router.push({ path: `/pay/${order.value.payNo}`, query: { orderId: order.value.id, amount: order.value.onlineAmount } });
}

async function cancel() {
  try {
    await showConfirmDialog({ title: '取消订单', message: '取消后将退回已抵扣的预存余额，确定取消吗？' });
  } catch (e) {
    return;
  }
  await orderApi.cancel(order.value.id);
  showToast('订单已取消');
  load();
}

async function confirm() {
  try {
    await showConfirmDialog({ title: '确认收货', message: '请确认已收到全部商品' });
  } catch (e) {
    return;
  }
  await orderApi.confirm(order.value.id);
  showToast('已确认收货');
  load();
}
</script>

<style scoped>
.detail-page {
  padding-bottom: 90px;
}

.loading {
  display: block;
  text-align: center;
  padding: 60px 0;
}

.status-card {
  padding: 22px 18px 26px;
  background: linear-gradient(150deg, #2b7cf5, #0f4fbf);
  color: #fff;
}

.status-text {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 6px;
}

.status-card .muted {
  color: rgba(255, 255, 255, 0.78);
}

.line-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid #f4f5f7;
}

.line-item:last-child {
  border-bottom: none;
}

.line-info {
  flex: 1;
  min-width: 0;
}

.amount-line {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 4px 0;
}

.kv {
  display: flex;
  gap: 10px;
  font-size: 13px;
  padding: 4px 0;
}

.kv span:first-child {
  width: 72px;
  flex-shrink: 0;
}

.addr {
  flex: 1;
}

.ship-images {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.ship-images img {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  object-fit: cover;
}
</style>
