<script setup>
import { computed, nextTick, onActivated, onDeactivated, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import Sortable from 'sortablejs';
import { categoryApi, productApi } from '../../api';

defineOptions({ name: 'ProductListView' });

const router = useRouter();

const keyword = ref('');
const appliedKeyword = ref('');
const groups = ref([]);
const activeGroupId = ref('');   // '' 表示全部
const products = ref([]);
const loading = ref(false);

const groupName = computed(() => {
  const hit = groups.value.find((g) => g.id === activeGroupId.value);
  return hit ? hit.name : '全部商品';
});

async function loadGroups() {
  groups.value = (await categoryApi.list()) || [];
}

async function loadProducts() {
  loading.value = true;
  try {
    const params = { page: 1, size: 200 };
    if (appliedKeyword.value) params.keyword = appliedKeyword.value;
    if (activeGroupId.value !== '') params.categoryId = activeGroupId.value;
    const res = await productApi.page(params);
    products.value = (res && res.records) || [];
  } finally {
    loading.value = false;
  }
}

function search() {
  appliedKeyword.value = keyword.value.trim();
  loadProducts();
}

function pickGroup(id) {
  if (activeGroupId.value === id) return;
  activeGroupId.value = id;
  loadProducts();
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

// ---------- 排序弹窗 ----------
const sortVisible = ref(false);
const sortGroups = ref([]);
const sortProducts = ref([]);
const sortGroupId = ref('');
const sortSaving = ref(false);
const groupListEl = ref(null);
const productListEl = ref(null);
let groupSortable = null;
let productSortable = null;

const sortGroupName = computed(() => {
  const hit = sortGroups.value.find((g) => g.id === sortGroupId.value);
  return hit ? hit.name : '';
});

async function openSort() {
  sortGroups.value = (await categoryApi.list()).map((c) => ({ id: c.id, name: c.name }));
  sortProductId(sortGroups.value.length ? sortGroups.value[0].id : '');
  sortVisible.value = true;
  await nextTick();
  bindSortable();
  if (sortGroups.value.length) await loadSortProducts(sortGroups.value[0].id);
}

function sortProductId(id) {
  sortGroupId.value = id;
}

async function loadSortProducts(groupId) {
  if (groupId === '' || groupId === null || groupId === undefined) {
    sortProducts.value = [];
    return;
  }
  sortGroupId.value = groupId;
  const res = await productApi.page({ categoryId: groupId, page: 1, size: 200 });
  sortProducts.value = ((res && res.records) || [])
    .slice()
    .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
    .map((p) => ({ id: p.id, name: p.name, cover: p.cover }));
  await nextTick();
  if (productSortable) productSortable.destroy();
  bindProductSortable();
}

function bindSortable() {
  if (groupListEl.value) {
    if (groupSortable) groupSortable.destroy();
    groupSortable = Sortable.create(groupListEl.value, {
      animation: 150,
      handle: '.drag-handle',
      ghostClass: 'sort-ghost'
    });
  }
  bindProductSortable();
}

function bindProductSortable() {
  if (!productListEl.value) return;
  productSortable = Sortable.create(productListEl.value, {
    animation: 150,
    handle: '.drag-handle',
    ghostClass: 'sort-ghost'
  });
}

function closeSort() {
  sortVisible.value = false;
  if (groupSortable) {
    groupSortable.destroy();
    groupSortable = null;
  }
  if (productSortable) {
    productSortable.destroy();
    productSortable = null;
  }
}

async function saveSort() {
  sortSaving.value = true;
  try {
    if (sortGroups.value.length) {
      // 按 DOM 顺序读取，拖拽只改视图顺序
      const groupIds = [...(groupListEl.value ? groupListEl.value.children : [])]
        .map((el) => Number(el.dataset.id))
        .filter((id) => !Number.isNaN(id));
      if (groupIds.length) await categoryApi.sort(groupIds);
    }
    if (sortProducts.value.length && productListEl.value) {
      const productIds = [...productListEl.value.children]
        .map((el) => Number(el.dataset.id))
        .filter((id) => !Number.isNaN(id));
      if (productIds.length) await productApi.sort(productIds);
    }
    showToast('排序已保存');
    closeSort();
    await loadGroups();
    await loadProducts();
  } finally {
    sortSaving.value = false;
  }
}

onActivated(async () => {
  await loadGroups();
  await loadProducts();
});

onDeactivated(closeSort);
</script>

<template>
  <div class="page">
    <div class="top-bar">
      <van-search
        v-model="keyword"
        placeholder="搜索商品名称"
        shape="round"
        background="#fff"
        @search="search"
        @clear="search"
      />
      <van-button size="small" type="primary" class="top-btn" @click="search">搜索</van-button>
      <van-button size="small" class="top-btn" @click="openSort">排序</van-button>
    </div>

    <div class="main">
      <div class="side">
        <div
          class="group-item"
          :class="{ active: activeGroupId === '' }"
          @click="pickGroup('')"
        >全部</div>
        <div
          v-for="g in groups"
          :key="g.id"
          class="group-item"
          :class="{ active: activeGroupId === g.id }"
          @click="pickGroup(g.id)"
        >{{ g.name }}</div>
      </div>

      <div class="list">
        <div class="list-head">
          <span>{{ groupName }}</span>
          <van-icon name="plus" size="18" @click="router.push('/products/create')" />
        </div>
        <van-loading v-if="loading" class="loading" size="20">加载中...</van-loading>
        <template v-else>
          <div v-for="p in products" :key="p.id" class="product" @click="openDetail(p)">
            <van-image :src="p.cover" fit="cover" class="thumb" radius="6">
              <template #error><div class="thumb-empty">无图</div></template>
            </van-image>
            <div class="name">{{ p.name }}</div>
            <div class="status" :class="p.status === 1 ? 'on' : 'off'">
              {{ p.status === 1 ? '上架中' : '已下架' }}
            </div>
            <div class="switch" @click.stop>
              <van-switch
                :model-value="p.status === 1"
                size="18"
                @update:model-value="(v) => toggleStatus(p, v)"
              />
            </div>
          </div>
          <div v-if="!products.length" class="empty">没有更多了</div>
        </template>
      </div>
    </div>

    <van-popup
      :show="sortVisible"
      position="bottom"
      round
      :style="{ height: '72vh' }"
      @update:show="(v) => (v ? null : closeSort())"
    >
      <div class="sort-panel">
        <div class="sort-title">排序（长按 ⋮⋮ 拖动）</div>
        <div class="sort-body">
          <div class="sort-side">
            <div class="sort-sub">分组</div>
            <div ref="groupListEl" class="sort-list">
              <div
                v-for="g in sortGroups"
                :key="g.id"
                class="sort-item"
                :class="{ active: g.id === sortGroupId }"
                :data-id="g.id"
                @click="loadSortProducts(g.id)"
              >
                <span class="drag-handle">⋮⋮</span>
                <span class="sort-name">{{ g.name }}</span>
              </div>
            </div>
          </div>
          <div class="sort-main">
            <div class="sort-sub">{{ sortGroupName }} 商品</div>
            <div ref="productListEl" class="sort-list">
              <div
                v-for="p in sortProducts"
                :key="p.id"
                class="sort-item"
                :data-id="p.id"
              >
                <span class="drag-handle">⋮⋮</span>
                <span class="sort-name">{{ p.name }}</span>
              </div>
              <div v-if="!sortProducts.length" class="sort-empty">该分组暂无商品</div>
            </div>
          </div>
        </div>
        <div class="sort-footer">
          <van-button block plain round @click="closeSort">取消</van-button>
          <van-button block type="primary" round :loading="sortSaving" @click="saveSort">保存</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg);
  padding-bottom: 50px;
  box-sizing: border-box;
}

.top-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px 6px 0;
  background: #fff;
}

.top-bar :deep(.van-search) {
  flex: 1;
  padding-right: 4px;
}

.top-btn {
  flex-shrink: 0;
}

.main {
  flex: 1;
  display: flex;
  min-height: 0;
}

.side {
  width: 88px;
  flex-shrink: 0;
  overflow-y: auto;
  background: #f4f6f4;
}

.group-item {
  padding: 14px 6px;
  font-size: 13px;
  text-align: center;
  color: var(--text-sub);
  line-height: 1.3;
}

.group-item.active {
  background: #fff;
  color: var(--brand);
  font-weight: 600;
}

.list {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  background: #fff;
  padding-bottom: 12px;
}

.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  border-bottom: 1px solid #f0f2f0;
  position: sticky;
  top: 0;
  background: #fff;
  z-index: 2;
}

.loading {
  padding: 30px 0;
}

.product {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid #f5f7f5;
}

.thumb {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  background: #f4f6f4;
}

.thumb-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 10px;
  color: var(--text-sub);
}

.name {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status {
  flex-shrink: 0;
  font-size: 13px;
}

.status.on {
  color: #07c160;
}

.status.off {
  color: #a8abb2;
}

.switch {
  flex-shrink: 0;
}

.empty {
  padding: 40px 0;
  text-align: center;
  font-size: 12px;
  color: #b7bcc4;
}

/* 排序弹窗 */
.sort-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.sort-title {
  padding: 14px 16px 6px;
  font-size: 14px;
  font-weight: 600;
}

.sort-body {
  flex: 1;
  display: flex;
  min-height: 0;
  gap: 8px;
  padding: 0 12px;
}

.sort-side {
  width: 110px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.sort-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.sort-sub {
  font-size: 12px;
  color: var(--text-sub);
  padding: 4px 2px 6px;
}

.sort-list {
  flex: 1;
  overflow-y: auto;
  background: #f7f9f7;
  border-radius: 8px;
  padding: 6px;
}

.sort-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 6px;
  margin-bottom: 4px;
  background: #fff;
  border-radius: 6px;
  font-size: 12px;
}

.sort-side .sort-item.active {
  border: 1px solid var(--brand);
  color: var(--brand);
}

.drag-handle {
  color: #c8cdd4;
  cursor: grab;
  touch-action: none;
}

.sort-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sort-empty {
  padding: 20px 0;
  text-align: center;
  font-size: 12px;
  color: #b7bcc4;
}

.sort-ghost {
  opacity: 0.4;
  background: #e8f0e9 !important;
}

.sort-footer {
  display: flex;
  gap: 10px;
  padding: 10px 16px calc(12px + env(safe-area-inset-bottom));
  border-top: 1px solid #f0f2f0;
}
</style>
