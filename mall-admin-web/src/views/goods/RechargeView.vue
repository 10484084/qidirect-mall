<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { levelApi, rechargeApi } from '../../api';
import { fen2yuan } from '../../utils/format';

const loading = ref(false);
const rows = ref([]);
const total = ref(0);
const levels = ref([]);
const query = reactive({ keyword: '', status: null, page: 1, size: 20 });

const dialogVisible = ref(false);
const editing = ref(null);
const formRef = ref();
const form = reactive({
  title: '',
  limitPerStore: 0,
  timeRange: [],
  status: 1,
  levelIds: []
});

const statusOptions = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' }
];

async function load() {
  loading.value = true;
  try {
    const params = {};
    Object.entries(query).forEach(([k, v]) => {
      if (v !== '' && v !== null && v !== undefined) params[k] = v;
    });
    const res = await rechargeApi.page(params);
    rows.value = (res && res.records) || [];
    total.value = (res && res.total) || 0;
  } finally {
    loading.value = false;
  }
}

function search() {
  query.page = 1;
  load();
}

function openCreate() {
  editing.value = null;
  Object.assign(form, { title: '', limitPerStore: 0, timeRange: [], status: 1, levelIds: [] });
  dialogVisible.value = true;
}

function openEdit(row) {
  editing.value = row;
  Object.assign(form, {
    title: row.title,
    limitPerStore: row.limitPerStore || 0,
    timeRange: row.startTime && row.endTime ? [row.startTime, row.endTime] : [],
    status: row.status ?? 1,
    levelIds: [...(row.levelIds || [])]
  });
  dialogVisible.value = true;
}

async function submit() {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  const payload = {
    title: form.title,
    limitPerStore: Number(form.limitPerStore) || 0,
    startTime: form.timeRange && form.timeRange[0] ? form.timeRange[0] : null,
    endTime: form.timeRange && form.timeRange[1] ? form.timeRange[1] : null,
    status: form.status,
    levelIds: form.levelIds
  };
  if (editing.value) {
    await rechargeApi.update(editing.value.id, payload);
  } else {
    await rechargeApi.create(payload);
  }
  ElMessage.success('已保存');
  dialogVisible.value = false;
  load();
}

async function toggle(row) {
  const next = row.status === 1 ? 0 : 1;
  await rechargeApi.changeStatus(row.id, next);
  ElMessage.success('状态已更新');
  load();
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除预存活动「${row.title}」吗？删除后不可恢复；已产生预存订单的活动会被拒绝，请改用停用。`,
      '删除预存活动',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    );
  } catch (e) {
    return;
  }
  await rechargeApi.remove(row.id);
  ElMessage.success('已删除');
  load();
}

onMounted(async () => {
  levels.value = (await levelApi.list()) || [];
  load();
});
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">预存活动</h2>
      <div>
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="openCreate">新增活动</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-form class="filter-bar" inline>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="活动标题" clearable style="width: 200px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 110px">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="活动标题" min-width="160" />
        <el-table-column label="每店限额" width="120">
          <template #default="{ row }">
            <span v-if="row.limitPerStore">¥{{ fen2yuan(row.limitPerStore) }}</span>
            <span v-else class="text-muted">不限</span>
          </template>
        </el-table-column>
        <el-table-column label="适用档位" min-width="160">
          <template #default="{ row }">{{ (row.levelNames || []).join('、') || '全部' }}</template>
        </el-table-column>
        <el-table-column label="活动时间" min-width="200">
          <template #default="{ row }">
            <span v-if="row.startTime || row.endTime">{{ row.startTime || '—' }} ~ {{ row.endTime || '—' }}</span>
            <span v-else class="text-muted">长期</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="toggle(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="load"
          @size-change="search"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑预存活动' : '新增预存活动'" width="560px">
      <el-form ref="formRef" :model="form" label-width="110px">
        <el-form-item label="活动标题" prop="title" :rules="[{ required: true, message: '请输入活动标题' }]">
          <el-input v-model="form.title" maxlength="64" />
        </el-form-item>
        <el-form-item label="每店限额(元)">
          <el-input-number
            :model-value="(form.limitPerStore || 0) / 100"
            :min="0"
            :precision="2"
            :step="1000"
            style="width: 180px"
            @update:model-value="form.limitPerStore = Math.round(($event || 0) * 100)"
          />
          <span class="text-muted ml8">0 表示不限</span>
        </el-form-item>
        <el-form-item label="活动时间">
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item
          label="适用档位"
          prop="levelIds"
          :rules="[{ type: 'array', required: true, message: '请选择适用档位' }]"
        >
          <el-select v-model="form.levelIds" multiple placeholder="请选择档位" style="width: 100%">
            <el-option v-for="l in levels" :key="l.id" :label="l.name" :value="l.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" />
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
