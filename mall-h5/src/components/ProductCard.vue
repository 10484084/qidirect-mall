<template>
  <div class="product-card" @click="$router.push(`/products/${product.id}`)">
    <div class="cover-box">
      <img
        v-if="showImg"
        :src="product.cover"
        alt=""
        @error="imgFailed = true"
      />
      <div v-else class="cover-empty">
        <van-icon name="photo-o" size="20" />
      </div>
    </div>
    <div class="info">
      <div class="name row-2">{{ product.name }}</div>
      <div class="spec">{{ specText }}</div>
      <div class="bottom">
        <span v-if="product.purchasable" class="money price">{{ fenToYuan(product.minPrice) }}</span>
        <span v-else class="muted small">当前档位暂不可购</span>
        <van-tag v-if="product.v3GiftEnabled === 1" class="gift-tag" color="#fff5e6" text-color="#d97706">买赠</van-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { fenToYuan } from '../utils/format';

const imgFailed = ref(false);
watch(() => props.product.cover, () => { imgFailed.value = false; });

const showImg = computed(() => !!props.product.cover && !imgFailed.value);

const props = defineProps({
  product: { type: Object, required: true }
});

const specText = computed(() => {
  const skus = props.product.skus || [];
  if (!skus.length) return '暂无规格';
  return skus.map((sku) => sku.specName).join(' / ');
});
</script>

<style scoped>
.product-card {
  display: flex;
  gap: 10px;
  background: #fff;
  border-radius: 12px;
  padding: 10px;
  margin-bottom: 10px;
  transition: transform 0.12s ease;
}

.product-card:active {
  transform: scale(0.99);
}

.cover-box {
  width: 96px;
  height: 96px;
  flex-shrink: 0;
  border-radius: 10px;
  overflow: hidden;
  background: #f5f7f5;
}

.cover-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: #c8cdd4;
}

.info {
  flex: 1;
  min-width: 0;
}

.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.name {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
  color: #1f2421;
}

.spec {
  margin-top: 4px;
  font-size: 12px;
  color: #9aa0a8;
}

.bottom {
  margin-top: auto;
  display: flex;
  align-items: baseline;
  gap: 2px;
  padding-top: 6px;
}

.price {
  font-size: 18px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.gift-tag {
  margin-left: auto;
  align-self: center;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 11px;
}
</style>
