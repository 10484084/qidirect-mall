<template>
  <div class="page checkout-page">
    <PageHeader title="确认订单" />

    <div class="page-body">
      <div v-if="eligibility && !eligibility.canOrder" class="card warn-card">
        <van-icon name="warning-o" />
        <span>{{ eligibility.reason || '当前不可下单' }}</span>
        <van-button size="mini" type="primary" round @click="goFix">去处理</van-button>
      </div>

      <div class="card">
        <div class="card-title">收货信息</div>
        <van-field v-model="form.receiverName" label="收货人" placeholder="请输入收货人" maxlength="32" />
        <van-field v-model="form.receiverPhone" label="手机号" type="tel" maxlength="20" placeholder="请输入收货手机号" />
        <van-field
          v-model="form.receiverAddr"
          label="收货地址"
          type="textarea"
          rows="2"
          autosize
          maxlength="255"
          placeholder="请输入详细收货地址"
        />
      </div>

      <div class="card">
        <div class="card-title">商品清单</div>
        <div v-for="item in items" :key="item.id" class="line-item">
          <div class="cover-box">
            <img v-if="item.cover" :src="item.cover" :alt="item.productName" />
            <span v-else>暂无</span>
          </div>
          <div class="line-info">
            <div class="row-2">{{ item.productName }}</div>
            <div class="muted small">{{ item.specName }} × {{ item.qty }}</div>
          </div>
          <span class="money">{{ fenToYuan(item.amount) }}</span>
        </div>

        <div v-if="giftLines.length" class="gift-box">
          <div class="gift-title">赠品（随主货发出，不计金额）</div>
          <div v-for="(gift, index) in giftLines" :key="index" class="gift-line">
            {{ gift.productName }} · {{ gift.specName }} × {{ gift.qty }} 瓶
            <span class="tag-gift">赠品</span>
          </div>
        </div>
      </div>

      <div class="card">
        <van-cell title="使用预存余额抵扣" :label="`预存余额 ¥${fenToYuan(depositBalance)}`">
          <template #right-icon>
            <van-switch v-model="useDeposit" size="20" />
          </template>
        </van-cell>
        <div class="amount-lines">
          <div class="flex-between"><span class="muted">商品合计</span><span>¥{{ fenToYuan(totalAmount) }}</span></div>
          <div class="flex-between"><span class="muted">预存抵扣</span><span>-¥{{ fenToYuan(depositAmount) }}</span></div>
          <div class="flex-between"><span class="muted">微信支付</span><span class="money">{{ fenToYuan(onlineAmount) }}</span></div>
        </div>
      </div>
    </div>

    <div class="footer-bar">
      <div class="grow total">
        应付 <span class="money">{{ fenToYuan(onlineAmount) }}</span>
      </div>
      <van-button type="primary" round :loading="submitting" :disabled="!items.length" @click="submit">
        提交订单
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showDialog, showToast } from 'vant';
import PageHeader from '../components/PageHeader.vue';
import { cartApi, orderApi, productApi, walletApi } from '../api';
import { useCartStore } from '../stores/cart';
import { useUserStore } from '../stores/user';
import { fenToYuan } from '../utils/format';
import { assertPayAllowed } from '../utils/pay';

const route = useRoute();
const router = useRouter();
const user = useUserStore();
const cart = useCartStore();

const items = ref([]);
const gifts = ref([]);
const depositBalance = ref(0);
const useDeposit = ref(true);
const eligibility = ref(null);
const submitting = ref(false);
const form = reactive({
  receiverName: '',
  receiverPhone: '',
  receiverAddr: ''
});

const totalAmount = computed(() => items.value.reduce((sum, item) => sum + (item.amount || 0), 0));
const depositAmount = computed(() => {
  if (!useDeposit.value) return 0;
  return Math.min(totalAmount.value, depositBalance.value);
});
const onlineAmount = computed(() => Math.max(totalAmount.value - depositAmount.value, 0));

const giftLines = computed(() => {
  const map = new Map();
  gifts.value.forEach((gift) => {
    const key = `${gift.productId}-${gift.skuId}`;
    const exists = map.get(key);
    if (exists) exists.qty += gift.qty;
    else map.set(key, { ...gift });
  });
  return Array.from(map.values());
});

onMounted(async () => {
  const ids = String(route.query.ids || '').split(',').map((v) => Number(v)).filter(Boolean);
  try {
    const [cartItems, walletData] = await Promise.all([cartApi.list(), walletApi.wallet()]);
    items.value = (cartItems || []).filter((item) => ids.includes(item.id));
    depositBalance.value = walletData.depositBalance || 0;
    if (!items.value.length) {
      showToast('结算商品已失效');
      router.replace('/cart');
      return;
    }
    form.receiverName = (user.profile && user.profile.name) || '';
    form.receiverPhone = (user.profile && user.profile.phone) || '';
    form.receiverAddr = '';
    eligibility.value = await user.loadEligibility();
    loadGifts();
  } catch (e) {
    // 错误已提示
  }
});

async function loadGifts() {
  const collected = [];
  for (const item of items.value) {
    try {
      const result = await productApi.giftPreview(item.productId, item.skuId, item.qty);
      (result || []).forEach((gift) => {
        collected.push({ ...gift, productName: item.productName });
      });
    } catch (e) {
      // 无赠品或非最高档位
    }
  }
  gifts.value = collected;
}

function goFix() {
  if (!user.authed) router.push('/profile');
  else router.push('/purchase');
}

async function submit() {
  if (eligibility.value && !eligibility.value.canOrder) {
    showToast(eligibility.value.reason || '当前不可下单');
    return;
  }
  if (!form.receiverName.trim() || !form.receiverPhone.trim() || !form.receiverAddr.trim()) {
    showToast('请填写完整收货信息');
    return;
  }
  if (onlineAmount.value > 0 && !assertPayAllowed()) return;

  submitting.value = true;
  try {
    const order = await orderApi.create({
      cartItemIds: items.value.map((item) => item.id),
      receiverName: form.receiverName.trim(),
      receiverPhone: form.receiverPhone.trim(),
      receiverAddr: form.receiverAddr.trim(),
      useDeposit: useDeposit.value
    });
    cart.refresh().catch(() => {});
    if (order.payNo) {
      router.replace({ path: `/pay/${order.payNo}`, query: { orderId: order.id, amount: order.onlineAmount } });
      return;
    }
    await showDialog({
      title: '下单成功',
      message: '预存余额已全额抵扣，订单进入待发货状态。',
      confirmButtonText: '查看订单'
    });
    router.replace(`/orders/${order.id}`);
  } catch (e) {
    // 错误已提示
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.checkout-page {
  padding-bottom: 90px;
}

.warn-card {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #ed6a0c;
  background: #fff8ef;
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

.gift-box {
  margin-top: 10px;
  background: #fff7ef;
  border-radius: 8px;
  padding: 10px;
}

.gift-title {
  font-size: 12px;
  color: #ff7d00;
  margin-bottom: 4px;
}

.gift-line {
  font-size: 13px;
  padding: 2px 0;
}

.amount-lines {
  padding: 8px 4px 2px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
}

.total {
  font-size: 13px;
}
</style>
