<script setup>
import { computed, onActivated, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { levelApi, storeApi } from '../../api';
import { AUTH_STATUS, STORE_STATUS } from '../../utils/dict';
import { fen2yuan } from '../../utils/format';

defineOptions({ name: 'StoreListView' });

const router = useRouter();

const keyword = ref('');
const searchText = ref('');
const status = ref('');
const levelId = ref('');
const levels = ref([]);

const statusOptions = [
  { text: '全部状态', value: '' },
  { text: '正常', value: 1 },
  { text: '冻结', value: 2 }
];

/**
 * 必须用 computed 缓存：内联数组每次渲染都是新引用，
 * 会让 van-dropdown-menu 反复 setOptions 触发递归更新，页面直接假死。
 */
const levelOptions = computed(() => [
  { text: '全部档位', value: '' },
  ...levels.value.map((l) => ({ text: l.name, value: l.id }))
]);

const stores = ref([]);
const page = ref(1);
const size = 20;
const loading = ref(false);
const finished = ref(false);
const refreshing = ref(false);

async function fetchPage() {
  const params = { page: page.value, size };
  if (keyword.value) params.keyword = keyword.value;
  if (status.value !== '') params.status = status.value;
  if (levelId.value !== '') params.levelId = levelId.value;
  const res = await storeApi.page(params);
  const records = (res && res.records) || [];
  stores.value = page.value === 1 ? records : stores.value.concat(records);
  const total = (res && res.total) || 0;
  if (stores.value.length >= total || records.length === 0) {
    finished.value = true;
  }
  page.value += 1;
}

async function resetList() {
  page.value = 1;
  finished.value = false;
  stores.value = [];
  loading.value = true;
  try {
    await fetchPage();
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

async function onLoad() {
  if (finished.value) {
    loading.value = false;
    return;
  }
  try {
    await fetchPage();
  } finally {
    loading.value = false;
  }
}

function onSearch() {
  keyword.value = searchText.value.trim();
  resetList();
}

function onFilterChange() {
  resetList();
}

function storeStatus(status1) {
  const meta = STORE_STATUS[status1] || { label: '-', type: 'info' };
  const cls = { success: 'tag-success', danger: 'tag-danger' }[meta.type] || 'tag-gray';
  return { label: meta.label, cls };
}

function authLabel(v) {
  return (AUTH_STATUS[v] || { label: '-' }).label;
}

onMounted(async () => {
  resetList();
  try {
    levels.value = (await levelApi.list()) || [];
  } catch (e) {
    /* 档位加载失败不阻塞门店列表 */
  }
});

let firstActivate = true;
onActivated(() => {
  if (firstActivate) {
    firstActivate = false;
    return;
  }
  resetList();
});
</script>

<template>
  <div class="page">
    <div class="sticky-bar">
      <van-search
        v-model="searchText"
        placeholder="门店名称 / 手机号 / 邀请码"
        shape="round"
        background="#fff"
        @search="onSearch"
        @clear="onSearch"
      />
      <van-dropdown-menu active-color="#326940">
        <van-dropdown-item v-model="status" :options="statusOptions" @change="onFilterChange" />
        <van-dropdown-item v-model="levelId" :options="levelOptions" @change="onFilterChange" />
      </van-dropdown-menu>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="resetList">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        :immediate-check="false"
        @load="onLoad"
      >
        <div v-for="store in stores" :key="store.id" class="card" @click="router.push(`/stores/${store.id}`)">
          <div class="row">
            <span class="name ellipsis">{{ store.name || store.nickname || '未命名门店' }}</span>
            <span class="tag-text" :class="storeStatus(store.status).cls">{{ storeStatus(store.status).label }}</span>
          </div>

          <div class="row">
            <span class="muted">{{ store.phone }}</span>
            <span class="muted">ID {{ store.id }}</span>
          </div>

          <div class="row meta">
            <span class="muted ellipsis">上级：{{ store.parentName || '无' }}</span>
            <span class="tag-text tag-gray">{{ store.levelName || '未加盟' }}</span>
          </div>

          <div class="row">
            <span class="muted">
              实名 {{ authLabel(store.authStatus) }}
              <span v-if="store.duplicateFlag" class="dup">· 疑似重复</span>
            </span>
            <span v-if="store.levelPaidAmount" class="muted">加盟 ¥{{ fen2yuan(store.levelPaidAmount) }}</span>
          </div>

          <div class="row">
            <span class="muted">{{ store.joinTime || '-' }}</span>
            <span class="go">查看详情 ›</span>
          </div>
        </div>

        <div v-if="!loading && !stores.length" class="empty-tip">没有符合条件的门店</div>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.sticky-bar {
  margin: -12px -12px 8px;
  padding-bottom: 0;
}

.sticky-bar :deep(.van-search) {
  padding-bottom: 6px;
}

.name {
  font-size: 15px;
  font-weight: 600;
  max-width: 68%;
}

.meta {
  margin-top: 6px;
}

.dup {
  color: #d93025;
}

.go {
  font-size: 12px;
  color: var(--brand);
}
</style>
