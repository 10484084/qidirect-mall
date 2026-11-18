<template>
  <div class="page with-tab">
    <div class="search-bar">
      <van-search
        v-model="keyword"
        placeholder="搜索商品名称"
        shape="round"
        @search="load"
        @clear="load"
      />
    </div>

    <van-tabs v-model:active="categoryId" sticky @change="load">
      <van-tab title="全部" :name="0" />
      <van-tab v-for="item in categories" :key="item.id" :title="item.name" :name="item.id" />
    </van-tabs>

    <div class="page-body">
      <van-loading v-if="loading" class="loading" />
      <template v-else>
        <ProductCard v-for="item in products" :key="item.id" :product="item" />
        <div v-if="!products.length" class="text-empty">暂无商品</div>
      </template>
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

async function load() {
  loading.value = true;
  try {
    products.value = await productApi.list({
      categoryId: categoryId.value || undefined,
      keyword: keyword.value || undefined
    }) || [];
  } catch (e) {
    products.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  load();
  try {
    categories.value = await productApi.categories() || [];
  } catch (e) {
    categories.value = [];
  }
});
</script>

<style scoped>
.search-bar {
  background: #fff;
}

.loading {
  display: block;
  text-align: center;
  padding: 40px 0;
}
</style>
