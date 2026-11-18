<template>
  <div class="product-card" @click="$router.push(`/products/${product.id}`)">
    <div class="cover-box">
      <img v-if="product.cover" :src="product.cover" :alt="product.name" />
      <span v-else>暂无图片</span>
    </div>
    <div class="info">
      <div class="name row-2">{{ product.name }}</div>
      <div class="muted small">{{ specText }}</div>
      <div class="flex-between mt-8">
        <div>
          <span v-if="product.purchasable" class="money">{{ fenToYuan(product.minPrice) }}</span>
          <span v-else class="muted small">当前档位暂不可购</span>
        </div>
        <van-tag v-if="product.v3GiftEnabled === 1" type="warning" plain>买赠</van-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { fenToYuan } from '../utils/format';

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
  gap: 12px;
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 10px;
}

.cover-box {
  width: 88px;
  height: 88px;
}

.info {
  flex: 1;
  min-width: 0;
}

.name {
  font-size: 14px;
  font-weight: 500;
  line-height: 1.35;
}

.money {
  font-size: 16px;
}
</style>
