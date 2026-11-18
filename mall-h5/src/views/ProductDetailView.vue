<template>
  <div class="page detail-page">
    <PageHeader title="商品详情" />

    <van-loading v-if="loading" class="loading" />
    <template v-else-if="product">
      <van-swipe class="swipe" :autoplay="4000" indicator-color="#fff">
        <van-swipe-item v-for="(img, index) in gallery" :key="index">
          <img v-if="img" :src="img" :alt="product.name" class="swipe-img" />
          <div v-else class="swipe-placeholder">暂无图片</div>
        </van-swipe-item>
      </van-swipe>

      <div class="card price-card">
        <div class="price-line">
          <span v-if="selectedSku && selectedSku.purchasable" class="money price">
            {{ fenToYuan(selectedSku.price) }}
          </span>
          <span v-else class="muted">当前档位该规格不可购买</span>
          <van-tag v-if="product.v3GiftEnabled === 1" type="warning">V3 买赠</van-tag>
        </div>
        <div class="name">{{ product.name }}</div>
        <div class="muted small" v-if="product.description">{{ product.description }}</div>
      </div>

      <div class="card">
        <div class="card-title">选择规格</div>
        <div class="sku-list">
          <div
            v-for="sku in product.skus"
            :key="sku.id"
            class="sku-item"
            :class="{ active: sku.id === skuId, disabled: !sku.purchasable }"
            @click="selectSku(sku)"
          >
            <div class="sku-name">{{ sku.specName }}</div>
            <div class="sku-price">{{ sku.purchasable ? `¥${fenToYuan(sku.price)}` : '不可购' }}</div>
          </div>
        </div>

        <div class="flex-between mt-12">
          <span class="muted">购买数量</span>
          <van-stepper v-model="qty" :min="1" :max="999" integer @change="loadGift" />
        </div>

        <div v-if="gifts.length" class="gift-box">
          <div class="gift-title">买赠预览（仅最高档位参与）</div>
          <div v-for="(gift, index) in gifts" :key="index" class="gift-line">
            {{ gift.specName }} × {{ gift.qty }} 瓶
            <span class="tag-gift">赠品</span>
          </div>
        </div>
        <div v-else-if="product.v3GiftEnabled === 1 && product.giftRule" class="muted small mt-8">
          买赠规则：每满 {{ product.giftRule.thresholdQty }} 瓶送 {{ product.giftRule.giftQty }} 瓶
        </div>
      </div>

      <div class="footer-bar">
        <van-button icon="cart-o" round plain type="primary" @click="addToCart(false)">加入购物车</van-button>
        <van-button class="grow" round type="primary" @click="addToCart(true)">立即购买</van-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import PageHeader from '../components/PageHeader.vue';
import { cartApi, productApi } from '../api';
import { useCartStore } from '../stores/cart';
import { useUserStore } from '../stores/user';
import { fenToYuan } from '../utils/format';

const route = useRoute();
const router = useRouter();
const cart = useCartStore();
const user = useUserStore();

const loading = ref(true);
const product = ref(null);
const skuId = ref(null);
const qty = ref(1);
const gifts = ref([]);

const gallery = computed(() => {
  if (!product.value) return [];
  const images = product.value.images && product.value.images.length ? product.value.images : [];
  return images.length ? images : [product.value.cover || ''];
});

const selectedSku = computed(() => {
  if (!product.value) return null;
  return (product.value.skus || []).find((sku) => sku.id === skuId.value) || null;
});

onMounted(async () => {
  try {
    product.value = await productApi.detail(route.params.id);
    const first = (product.value.skus || []).find((sku) => sku.purchasable) || (product.value.skus || [])[0];
    if (first) {
      skuId.value = first.id;
      loadGift();
    }
  } finally {
    loading.value = false;
  }
});

function selectSku(sku) {
  if (!sku.purchasable) {
    showToast('当前档位未配置该规格价格');
    return;
  }
  skuId.value = sku.id;
  loadGift();
}

async function loadGift() {
  if (!product.value || !skuId.value) return;
  try {
    gifts.value = await productApi.giftPreview(product.value.id, skuId.value, qty.value) || [];
  } catch (e) {
    gifts.value = [];
  }
}

function guard() {
  if (!user.authed) {
    showToast('请先完成实名认证');
    router.push('/profile');
    return false;
  }
  if (!user.joined) {
    showToast('请先加盟后下单');
    router.push('/purchase');
    return false;
  }
  return true;
}

async function addToCart(buyNow) {
  if (!selectedSku.value || !selectedSku.value.purchasable) {
    showToast('请选择可购买的规格');
    return;
  }
  if (!guard()) return;
  try {
    const item = await cartApi.add(skuId.value, qty.value);
    await cart.refresh().catch(() => {});
    if (buyNow) {
      router.push({ path: '/checkout', query: { ids: String(item.id) } });
    } else {
      showToast('已加入购物车');
    }
  } catch (e) {
    // 错误已提示
  }
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

.swipe {
  height: 300px;
  background: #fff;
}

.swipe-img {
  width: 100%;
  height: 300px;
  object-fit: cover;
}

.swipe-placeholder {
  width: 100%;
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9aa4b2;
  background: linear-gradient(135deg, #eef3ff, #dfe9ff);
}

.price-card {
  margin-top: -12px;
  position: relative;
  border-radius: 16px 16px var(--card-radius) var(--card-radius);
}

.price-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.price {
  font-size: 24px;
}

.name {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
  margin-bottom: 4px;
}

.sku-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.sku-item {
  min-width: 96px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #fafbfc;
  text-align: center;
}

.sku-item.active {
  border-color: var(--brand);
  background: #eef4ff;
  color: var(--brand);
}

.sku-item.disabled {
  opacity: 0.45;
}

.sku-name {
  font-size: 13px;
}

.sku-price {
  font-size: 12px;
  margin-top: 3px;
}

.gift-box {
  margin-top: 12px;
  background: #fff7ef;
  border-radius: 8px;
  padding: 10px;
}

.gift-title {
  font-size: 12px;
  color: #ff7d00;
  margin-bottom: 6px;
}

.gift-line {
  font-size: 13px;
  padding: 2px 0;
}
</style>
