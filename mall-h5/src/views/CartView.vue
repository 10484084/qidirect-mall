<template>
  <div class="page with-tab cart-page">
    <div class="head">
      <div class="flex-between">
        <span class="title">购物车</span>
        <span v-if="items.length" class="muted small" @click="clearAll">清空</span>
      </div>
    </div>

    <div class="page-body">
      <van-loading v-if="loading" class="loading" />
      <template v-else>
        <van-swipe-cell v-for="item in items" :key="item.id">
          <div class="card item-card">
            <van-checkbox
              :model-value="selected.has(item.id)"
              :disabled="!item.purchasable"
              @update:model-value="(v) => toggle(item.id, v)"
            />
            <div class="cover-box">
              <img v-if="item.cover" :src="item.cover" :alt="item.productName" />
              <span v-else>暂无</span>
            </div>
            <div class="item-info">
              <div class="item-name row-2">{{ item.productName }}</div>
              <div class="muted small">{{ item.specName }}<span v-if="item.factor > 1">（{{ item.factor }} 瓶/件）</span></div>
              <div class="flex-between mt-8">
                <span v-if="item.purchasable" class="money">{{ fenToYuan(item.unitPrice) }}</span>
                <span v-else class="muted small">当前档位不可购</span>
                <van-stepper
                  :model-value="item.qty"
                  :min="1"
                  :max="999"
                  integer
                  @change="(v) => changeQty(item, v)"
                />
              </div>
            </div>
          </div>
          <template #right>
            <van-button square type="danger" text="删除" class="delete-btn" @click="removeItem(item)" />
          </template>
        </van-swipe-cell>
        <div v-if="!items.length" class="text-empty">
          购物车还是空的
          <div class="mt-12">
            <van-button size="small" type="primary" round @click="$router.push('/products')">去选购</van-button>
          </div>
        </div>
      </template>
    </div>

    <div v-if="items.length" class="footer-bar">
      <van-checkbox :model-value="allChecked" @update:model-value="toggleAll">全选</van-checkbox>
      <div class="grow total">
        合计 <span class="money">{{ fenToYuan(totalAmount) }}</span>
      </div>
      <van-button type="primary" round :disabled="!selectedIds.length" @click="checkout">
        结算（{{ selectedIds.length }}）
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showConfirmDialog, showToast } from 'vant';
import { cartApi } from '../api';
import { useCartStore } from '../stores/cart';
import { fenToYuan } from '../utils/format';

const router = useRouter();
const cart = useCartStore();
const items = ref([]);
const selected = ref(new Set());
const loading = ref(true);

const selectedItems = computed(() => items.value.filter((item) => selected.value.has(item.id)));
const selectedIds = computed(() => selectedItems.value.map((item) => item.id));
const totalAmount = computed(() => selectedItems.value.reduce((sum, item) => sum + (item.amount || 0), 0));
const allChecked = computed(() => items.value.length > 0 && selected.value.size === items.value.length);

function toggle(id, checked) {
  const next = new Set(selected.value);
  if (checked) next.add(id);
  else next.delete(id);
  selected.value = next;
}

function toggleAll(checked) {
  selected.value = checked ? new Set(items.value.filter((i) => i.purchasable).map((i) => i.id)) : new Set();
}

async function load() {
  loading.value = true;
  try {
    items.value = await cartApi.list() || [];
    selected.value = new Set(items.value.filter((i) => i.purchasable).map((i) => i.id));
    cart.count = items.value.reduce((sum, item) => sum + (item.qty || 0), 0);
  } finally {
    loading.value = false;
  }
}

async function changeQty(item, value) {
  if (value === item.qty) return;
  try {
    await cartApi.updateQty(item.id, value);
    await load();
  } catch (e) {
    await load();
  }
}

async function removeItem(item) {
  await cartApi.remove(item.id);
  await load();
}

async function clearAll() {
  try {
    await showConfirmDialog({ title: '清空购物车', message: '确定清空全部商品吗？' });
  } catch (e) {
    return;
  }
  await cartApi.clear();
  await load();
}

function checkout() {
  const invalid = selectedItems.value.some((item) => !item.purchasable);
  if (invalid) {
    showToast('存在当前档位不可购的商品');
    return;
  }
  router.push({ path: '/checkout', query: { ids: selectedIds.value.join(',') } });
}

onMounted(load);
</script>

<style scoped>
.head {
  background: #fff;
  padding: 14px 16px;
  border-bottom: 1px solid #f2f3f5;
}

.title {
  font-size: 17px;
  font-weight: 600;
}

.cart-page {
  padding-bottom: 80px;
}

.loading {
  display: block;
  padding: 40px 0;
  text-align: center;
}

.item-card {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: 14px;
  line-height: 1.35;
}

.delete-btn {
  height: 100%;
}

.total {
  font-size: 13px;
}
</style>
