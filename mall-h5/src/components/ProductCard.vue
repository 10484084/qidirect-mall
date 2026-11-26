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
  gap: 12px;
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 10px;
}

.cover-box {
  width: 88px;
  height: 88px;
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

.name {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.35;
  color: #1f2421;
}

.money {
  font-size: 17px;
  font-weight: 600;
}
</style>
