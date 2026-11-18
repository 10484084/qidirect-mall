<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { giftRuleApi, productApi } from '../../api';

const loading = ref(false);
const rows = ref([]);
const products = ref([]);
const productId = ref(null);

const dialogVisible = ref(false);
const editing = ref(null);
const formRef = ref();
const form = reactive({
  productId: null,
  giftSkuId: null,
  thresholdQty: 1,
  giftQty: 1,
  enabled: 1,
  timeRange: []
});

const editingProduct = ref(null);

const skuOptions = computed(() => {
  const p = products.value.find((x) => x.id === form.productId) || editingProduct.value;
  return (p && p.skus) || [];
});

async function load() {
  loading.value = true;
  try {
    rows.value = (await giftRuleApi.list(productId.value || undefined)) || [];
  } finally {
    loading.value = false;
  }
}

async function loadProducts() {
  const res = await productApi.page({ page: 1, size: 200 });
  products.value = (res && res.records) || [];
}

function productName(id) {
  const p = products.value.find((x) => x.id === id);
  return p ? p.name : `#${id}`;
}

async function openCreate() {
  editing.value = null;
  editingProduct.value = null;
  Object.assign(form, {
    productId: productId.value || null,
    giftSkuId: null,
    thresholdQty: 1,
    giftQty: 1,
    enabled: 1,
    timeRange: []
  });
  dialogVisible.value = true;
}

async function openEdit(row) {
  editing.value = row;
  editingProduct.value = await productApi.detail(row.productId);
  Object.assign(form, {
    productId: row.productId,
    giftSkuId: row.giftSkuId,
    thresholdQty: row.thresholdQty,
    giftQty: row.giftQty,
    enabled: row.enabled,
    timeRange: row.startTime && row.endTime ? [row.startTime, row.endTime] : []
  });
  dialogVisible.value = true;
}

async function onProductChange() {
  form.giftSkuId = null;
  editingProduct.value = form.productId ? await productApi.detail(form.productId) : null;
}

async function submit() {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  const payload = {
    productId: form.productId,
    giftSkuId: form.giftSkuId,
    thresholdQty: Number(form.thresholdQty),
    giftQty: Number(form.giftQty),
    enabled: form.enabled,
    startTime: form.timeRange && form.timeRange[0] ? form.timeRange[0] : null,
    endTime: form.timeRange && form.timeRange[1] ? form.timeRange[1] : null
  };
  if (editing.value) {
    await giftRuleApi.update(editing.value.id, payload);
  } else {
    await giftRuleApi.create(payload);
  }
  ElMessage.success('已保存');
  dialogVisible.value = false;
  load();
}

async function toggle(row) {
  const next = row.enabled === 1 ? 0 : 1;
  try {
    await giftRuleApi.setEnabled(row.id, next === 1);
  } catch (e) {
    return;
  }
  ElMessage.success(next === 1 ? '已启用' : '已停用');
  load();
}

async function onRemove(row) {
  try {
    await ElMessageBox.confirm('确定删除该买赠规则吗？', '提示', { type: 'warning' });
  } catch (e) {
    return;
  }
  await giftRuleApi.remove(row.id);
  ElMessage.success('已删除');
  load();
}

onMounted(async () => {
  await loadProducts();
  load();
});
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">买赠规则</h2>
      <div>
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="openCreate">新增规则</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-form class="filter-bar" inline>
        <el-form-item label="商品">
          <el-select
            v-model="productId"
            clearable
            filterable
            placeholder="全部商品"
            style="width: 220px"
            @change="load"
          >
            <el-option v-for="p in products" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="商品" min-width="160">
          <template #default="{ row }">{{ productName(row.productId) }}</template>
        </el-table-column>
        <el-table-column prop="giftSkuName" label="赠品规格" min-width="140" />
        <el-table-column label="门槛" width="140">
          <template #default="{ row }">满 {{ row.thresholdQty }} 件</template>
        </el-table-column>
        <el-table-column label="赠品数量" width="100">
          <template #default="{ row }">赠 {{ row.giftQty }} 件</template>
        </el-table-column>
        <el-table-column label="有效期" min-width="180">
          <template #default="{ row }">
            <span v-if="row.startTime || row.endTime">{{ row.startTime || '—' }} ~ {{ row.endTime || '—' }}</span>
            <span v-else class="text-muted">长期</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="toggle(row)">{{ row.enabled === 1 ? '停用' : '启用' }}</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-alert
        class="mt16"
        type="info"
        :closable="false"
        show-icon
        title="每个商品仅允许一条启用中的买赠规则；商品需开启「最高档买赠」才对该商品生效。"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑买赠规则' : '新增买赠规则'" width="560px">
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-form-item label="商品" prop="productId" :rules="[{ required: true, message: '请选择商品' }]">
          <el-select v-model="form.productId" filterable placeholder="请选择商品" style="width: 100%" @change="onProductChange">
            <el-option v-for="p in products" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="赠品规格" prop="giftSkuId" :rules="[{ required: true, message: '请选择赠品规格' }]">
          <el-select v-model="form.giftSkuId" placeholder="请选择赠品规格" style="width: 100%">
            <el-option v-for="s in skuOptions" :key="s.id" :label="s.specName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="门槛数量" prop="thresholdQty" :rules="[{ required: true, message: '请输入门槛数量' }]">
          <el-input-number v-model="form.thresholdQty" :min="1" />
          <span class="text-muted ml8">件（按主商品件数）</span>
        </el-form-item>
        <el-form-item label="赠品数量" prop="giftQty" :rules="[{ required: true, message: '请输入赠品数量' }]">
          <el-input-number v-model="form.giftQty" :min="1" />
        </el-form-item>
        <el-form-item label="生效时间">
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ml8 {
  margin-left: 8px;
  font-size: 12px;
}
</style>
