<script setup>
import { computed, onActivated, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import { categoryApi, productApi } from '../../api';
import { fen2yuan } from '../../utils/format';

defineOptions({ name: 'ProductListView' });

const router = useRouter();

const keyword = ref('');
const categoryId = ref('');
const status = ref('');
const categories = ref([]);

const categoryOptions = computed(() => [
  { text: '全部分组', value: '' },
  ...categories.value.map((c) => ({ text: c.name, value: c.id }))
]);

const statusOptions = [
  { text: '全部状态', value: '' },
  { text: '上架中', value: 1 },
  { text: '已下架', value: 0 }
];

const products = ref([]);
const page = ref(1);
const size = 20;
const loading = ref(false);
const finished = ref(false);
const refreshing = ref(false);

function priceRange(product) {
  const prices = [];
  (product.skus || []).forEach((sku) => {
    (sku.prices || []).forEach((p) => {
      if (p.price > 0) prices.push(p.price);
    });
  });
  if (!prices.length) return '-';
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  return min === max ? `¥${fen2yuan(min)}` : `¥${fen2yuan(min)} ~ ${fen2yuan(max)}`;
}

async function fetchPage() {
  const params = { page: page.value, size };
  if (keyword.value) params.keyword = keyword.value;
  if (categoryId.value !== '') params.categoryId = categoryId.value;
  if (status.value !== '') params.status = status.value;
  const res = await productApi.page(params);
  const records = (res && res.records) || [];
  products.value = page.value === 1 ? records : products.value.concat(records);
  const total = (res && res.total) || 0;
  if (products.value.length >= total || records.length === 0) finished.value = true;
  page.value += 1;
}

async function resetList() {
  page.value = 1;
  finished.value = false;
  products.value = [];
  loading.value = true;
  try {
    await fetchPage();
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

async function loadMore() {
  if (finished.value) return;
  await fetchPage();
}

async function onRefresh() {
  refreshing.value = true;
  await resetList();
}

async function ensureCategories() {
  if (categories.value.length) return;
  categories.value = (await categoryApi.list()) || [];
}

async function toggleStatus(product, value) {
  const next = value ? 1 : 0;
  product.status = next;
  await productApi.setStatus(product.id, next);
  showToast(next === 1 ? '已上架' : '已下架');
}

function openDetail(product) {
  router.push(`/products/${product.id}`);
}

onActivated(async () => {
  await ensureCategories();
  await resetList();
});
</script>

<template>
  <div class="page">
    <van-sticky>
      <van-search
        v-model="keyword"
        placeholder="搜索商品名称"
        @search="resetList"
        @clear="resetList"
      />
      <van-dropdown-menu>
        <van-dropdown-item v-model="categoryId" :options="categoryOptions" @change="resetList" />
        <van-dropdown-item v-model="status" :options="statusOptions" @change="resetList" />
      </van-dropdown-menu>
    </van-sticky>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <div v-for="p in products" :key="p.id" class="product-item" @click="openDetail(p)">
          <van-image :src="p.cover" fit="cover" class="product-cover" radius="6">
            <template #error>
              <div class="cover-empty">无图</div>
            </template>
          </van-image>
          <div class="product-info">
            <div class="product-name">{{ p.name }}</div>
            <div class="product-meta">
              <span class="price">{{ priceRange(p) }}</span>
              <span class="muted">/ {{ p.unit }}</span>
            </div>
            <div class="product-tags">
              <van-tag v-if="p.recommend" type="warning" plain size="mini">推荐</van-tag>
              <van-tag v-if="p.v3GiftEnabled" type="success" plain size="mini">买赠</van-tag>
              <van-tag :type="p.status === 1 ? 'success' : 'default'" size="mini">
                {{ p.status === 1 ? '上架中' : '已下架' }}
              </van-tag>
            </div>
          </div>
          <div class="product-switch" @click.stop>
            <van-switch
              :model-value="p.status === 1"
              size="20"
              @update:model-value="(v) => toggleStatus(p, v)"
            />
          </div>
        </div>
        <van-empty v-if="!loading && !products.length" description="暂无商品" />
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
  padding-bottom: 20px;
}

.product-item {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 10px 12px;
  padding: 12px;
  background: #fff;
  border-radius: 10px;
}

.product-cover {
  width: 64px;
  height: 64px;
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

.product-info {
  flex: 1;
  min-width: 0;
}

.product-name {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-meta {
  font-size: 13px;
  margin-bottom: 6px;
}

.price {
  color: var(--money);
  font-weight: 600;
}

.product-tags {
  display: flex;
  gap: 4px;
}

.product-switch {
  padding-left: 6px;
}
</style>
