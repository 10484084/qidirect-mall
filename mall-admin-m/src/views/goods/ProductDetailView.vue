<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import { levelApi, productApi } from '../../api';
import { fen2yuan, yuan2fen } from '../../utils/format';

defineOptions({ name: 'ProductDetailView' });

const route = useRoute();
const router = useRouter();
const productId = Number(route.params.id);

const loading = ref(false);
const saving = ref(false);
const product = ref(null);
const levels = ref([]);
const priceDraft = ref({});

const sortedLevels = computed(() =>
  [...levels.value].sort((a, b) => (a.fee - b.fee) || (a.sort - b.sort))
);

function key(skuId, levelId) {
  return `${skuId}_${levelId}`;
}

function priceOf(sku, levelId) {
  const hit = (sku.prices || []).find((p) => p.levelId === levelId);
  return hit ? fen2yuan(hit.price) : '';
}

async function load() {
  loading.value = true;
  try {
    if (!levels.value.length) levels.value = (await levelApi.list()) || [];
    product.value = await productApi.detail(productId);
    const draft = {};
    (product.value.skus || []).forEach((sku) => {
      sortedLevels.value.forEach((l) => {
        draft[key(sku.id, l.id)] = priceOf(sku, l.id);
      });
    });
    priceDraft.value = draft;
  } finally {
    loading.value = false;
  }
}

function onPriceInput(skuId, levelId, value) {
  const cleaned = String(value ?? '')
    .replace(/[^\d.]/g, '')
    .replace(/^(\d*\.\d{2}).*$/, '$1');
  priceDraft.value[key(skuId, levelId)] = cleaned;
}

async function toggleStatus(value) {
  const next = value ? 1 : 0;
  product.value.status = next;
  await productApi.setStatus(productId, next);
  showToast(next === 1 ? '已上架' : '已下架');
}

async function savePrices() {
  if (!product.value) return;
  const payload = {
    categoryId: product.value.categoryId,
    name: product.value.name,
    unit: product.value.unit,
    cover: product.value.cover,
    images: product.value.images || [],
    description: product.value.description || '',
    v3GiftEnabled: product.value.v3GiftEnabled || 0,
    recommend: product.value.recommend || 0,
    status: product.value.status,
    sort: product.value.sort || 0,
    skus: (product.value.skus || []).map((sku, index) => ({
      id: sku.id,
      specName: sku.specName,
      factor: sku.factor || 1,
      sort: index + 1,
      prices: sortedLevels.value.map((l) => ({
        levelId: l.id,
        price: yuan2fen(priceDraft.value[key(sku.id, l.id)] || 0)
      }))
    }))
  };
  saving.value = true;
  try {
    await productApi.update(productId, payload);
    showToast('价格已保存');
    await load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <van-nav-bar title="商品详情" left-arrow @click-left="router.back()" />

    <van-loading v-if="loading" class="loading" size="24">加载中...</van-loading>

    <template v-else-if="product">
      <div class="card">
        <div class="head">
          <van-image :src="product.cover" fit="cover" class="cover" radius="8">
            <template #error><div class="cover-empty">无图</div></template>
          </van-image>
          <div class="head-info">
            <div class="name">{{ product.name }}</div>
            <div class="muted small">主单位：{{ product.unit || '-' }}</div>
            <div class="status-row">
              <span class="muted small">上架状态</span>
              <van-switch
                :model-value="product.status === 1"
                size="20"
                @update:model-value="toggleStatus"
              />
            </div>
          </div>
        </div>
        <div class="desc muted small">{{ product.description || '暂无商品描述' }}</div>
      </div>

      <div class="card">
        <div class="card-title">规格价格（元）</div>
        <div v-for="sku in product.skus" :key="sku.id" class="sku-block">
          <div class="sku-name">
            {{ sku.specName }}
            <span class="muted small">× {{ sku.factor }} {{ product.unit }}</span>
          </div>
          <div class="price-grid">
            <div v-for="l in sortedLevels" :key="l.id" class="price-cell">
              <div class="price-label">{{ l.name }}</div>
              <van-field
                :model-value="priceDraft[key(sku.id, l.id)]"
                type="number"
                placeholder="0.00"
                @update:model-value="(v) => onPriceInput(sku.id, l.id, v)"
              />
            </div>
          </div>
        </div>
        <van-button type="primary" block round :loading="saving" @click="savePrices">
          保存价格
        </van-button>
      </div>

      <div class="tip muted small">
        手机端支持改价与上下架；新增商品、增删规格、图片与买赠规则请在电脑端操作。
      </div>
    </template>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
  padding-bottom: 24px;
}

.loading {
  padding: 40px 0;
}

.card {
  margin: 10px 12px;
  padding: 14px;
  background: #fff;
  border-radius: 10px;
}

.head {
  display: flex;
  gap: 12px;
}

.cover {
  width: 76px;
  height: 76px;
  flex-shrink: 0;
  background: #f4f6f4;
}

.cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 12px;
  color: var(--text-sub);
}

.head-info {
  flex: 1;
  min-width: 0;
}

.name {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 6px;
}

.status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.desc {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f2f0;
  line-height: 1.6;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
}

.sku-block {
  padding: 10px 0;
  border-bottom: 1px dashed #eef0ee;
}

.sku-name {
  font-size: 14px;
  margin-bottom: 8px;
}

.price-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.price-label {
  font-size: 12px;
  color: var(--text-sub);
  margin-bottom: 2px;
}

.price-cell :deep(.van-cell) {
  padding: 4px 8px;
  background: #f7f9f7;
  border-radius: 6px;
}

.tip {
  padding: 0 20px;
  line-height: 1.6;
  text-align: center;
}
</style>
