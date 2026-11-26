<template>
  <div class="products-page">
    <div class="search-bar">
      <van-search v-model="keyword" placeholder="搜索商品名称" shape="round" @search="onSearch" @clear="onSearch" />
    </div>

    <div class="catalog">
      <aside class="cate-side">
        <div class="cate-item" :class="{ active: categoryId === 0 }" @click="selectCategory(0)">全部</div>
        <div
          v-for="item in categories"
          :key="item.id"
          class="cate-item"
          :class="{ active: categoryId === item.id }"
          @click="selectCategory(item.id)"
        >
          <span class="cate-name">{{ item.name }}</span>
        </div>
      </aside>

      <main ref="mainRef" class="goods-main">
        <van-loading v-if="loading" class="loading" />
        <template v-else>
          <ProductCard v-for="item in products" :key="item.id" :product="item" />
          <div v-if="!products.length" class="text-empty">暂无商品</div>
          <div v-else class="list-end">— 没有更多了 —</div>
        </template>
      </main>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import ProductCard from '../components/ProductCard.vue';
import { productApi } from '../api';

const keyword = ref('');
const categoryId = ref(0);
const categories = ref([]);
const products = ref([]);
const loading = ref(false);
const mainRef = ref();

async function load() {
  loading.value = true;
  try {
    products.value =
      (await productApi.list({
        categoryId: categoryId.value || undefined,
        keyword: keyword.value || undefined
      })) || [];
  } catch (e) {
    products.value = [];
  } finally {
    loading.value = false;
  }
}

function selectCategory(id) {
  if (categoryId.value === id) return;
  categoryId.value = id;
  if (mainRef.value) mainRef.value.scrollTop = 0;
  load();
}

function onSearch() {
  // 搜索跨分类，回到「全部」再查
  if (categoryId.value !== 0) {
    categoryId.value = 0;
  }
  load();
}

onMounted(async () => {
  load();
  try {
    categories.value = (await productApi.categories()) || [];
  } catch (e) {
    categories.value = [];
  }
});
</script>

<style scoped>
.products-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background: var(--page-bg);
}

.search-bar {
  flex-shrink: 0;
  background: #fff;
  padding-top: env(safe-area-inset-top);
  border-bottom: 1px solid #eef0f3;
}

.catalog {
  flex: 1;
  display: flex;
  min-height: 0;
}

/* 左侧分组 */
.cate-side {
  width: 88px;
  flex-shrink: 0;
  overflow-y: auto;
  background: #f5f6f8;
  padding-bottom: calc(66px + env(safe-area-inset-bottom));
  -webkit-overflow-scrolling: touch;
}

.cate-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 50px;
  padding: 8px 8px;
  font-size: 13px;
  line-height: 1.3;
  color: #5b6068;
  text-align: center;
  word-break: break-all;
}

.cate-item.active {
  background: #fff;
  color: var(--brand);
  font-weight: 600;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  border-radius: 0 10px 10px 0;
}

.cate-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: 0 2px 2px 0;
  background: var(--brand);
}

.cate-name {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 右侧商品 */
.goods-main {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  background: #f7f8fa;
  padding: 8px 10px calc(70px + env(safe-area-inset-bottom));
  -webkit-overflow-scrolling: touch;
}

.loading {
  display: block;
  text-align: center;
  padding: 40px 0;
}

.list-end {
  padding: 14px 0 6px;
  text-align: center;
  font-size: 12px;
  color: #c2c6cc;
}

.list-end {
  padding: 14px 0 6px;
  text-align: center;
  font-size: 12px;
  color: #c2c6cc;
}
</style>
