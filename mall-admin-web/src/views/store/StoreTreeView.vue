<script setup>
import { onMounted, ref } from 'vue';
import { storeApi } from '../../api';
import { STORE_STATUS, statusMeta } from '../../utils/dict';
import StoreDetailDrawer from './StoreDetailDrawer.vue';

const loading = ref(false);
const treeRef = ref();
const treeData = ref([]);
const drawer = ref();
const filterText = ref();
// 根节点首次加载的等待者：lazy 模式下 el-tree 先调用 loadNode(level=0)，需等接口返回后再 resolve
let rootResolver = null;

const defaultProps = {
  label: 'label',
  children: 'children',
  isLeaf: 'leaf'
};

function toNode(item) {
  const hasChildren = Number(item.childCount) > 0;
  return {
    id: item.id,
    label: `${item.name || item.nickname || '未命名'}（${item.phone}）${item.levelName ? ' · ' + item.levelName : ''}`,
    raw: item,
    leaf: !hasChildren
  };
}

async function loadRoot() {
  loading.value = true;
  try {
    const list = (await storeApi.tree()) || [];
    treeData.value = list.map(toNode);
  } finally {
    loading.value = false;
  }
  if (rootResolver) {
    const resolve = rootResolver;
    rootResolver = null;
    resolve(treeData.value);
  }
}

function loadNode(node, resolve) {
  if (node.level === 0) {
    // lazy 首次渲染就回调这里，此时接口可能未返回：先登记 resolver，等 loadRoot 完成后再 resolve
    if (loading.value) {
      rootResolver = resolve;
      return;
    }
    if (!treeData.value.length) {
      loading.value = true;
      storeApi
        .tree()
        .then((list) => {
          treeData.value = ((list || [])).map(toNode);
          resolve(treeData.value);
        })
        .catch(() => resolve([]))
        .finally(() => {
          loading.value = false;
        });
      return;
    }
    resolve(treeData.value);
    return;
  }
  storeApi
    .tree(node.data.id)
    .then((list) => resolve((list || []).map(toNode)))
    .catch(() => resolve([]));
}

function onNodeClick(data) {
  drawer.value.open(data.id);
}

function filterNode(value, data) {
  if (!value) return true;
  return (data.label || '').includes(value);
}

function onFilter() {
  treeRef.value.filter(filterText.value);
}

onMounted(loadRoot);
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">门店树</h2>
      <div class="tools">
        <el-input
          v-model="filterText"
          placeholder="按名称/手机号过滤已加载节点"
          clearable
          style="width: 240px"
          @input="onFilter"
        />
        <el-button @click="loadRoot">刷新</el-button>
      </div>
    </div>

    <el-card v-loading="loading" shadow="never">
      <el-tree
        ref="treeRef"
        :props="defaultProps"
        :load="loadNode"
        lazy
        node-key="id"
        :filter-node-method="filterNode"
        :expand-on-click-node="false"
        @node-click="onNodeClick"
      >
        <template #default="{ data }">
          <span class="tree-node">
            <span>{{ data.label }}</span>
            <el-tag
              v-if="statusMeta(STORE_STATUS, data.raw.status).label === '冻结'"
              type="danger"
              size="small"
            >
              冻结
            </el-tag>
            <span class="text-muted">
              {{ data.raw.childCount ? `下级 ${data.raw.childCount}` : '无下级' }}
            </span>
          </span>
        </template>
      </el-tree>
      <el-empty v-if="!loading && !treeData.length" description="暂无门店" />
    </el-card>

    <StoreDetailDrawer ref="drawer" />
  </div>
</template>

<style scoped>
.tools {
  display: flex;
  gap: 8px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
