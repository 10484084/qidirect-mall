<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">系统配置</span>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="rows" border stripe v-loading="loading">
        <el-table-column prop="cfgKey" label="配置项" width="260" />
        <el-table-column prop="cfgValue" label="配置值" min-width="220" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="editVisible" title="编辑配置" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="配置项">
          <el-input :model-value="form.cfgKey" disabled />
        </el-form-item>
        <el-form-item label="配置值" required>
          <el-input v-model="form.cfgValue" type="textarea" :rows="3" maxlength="512" show-word-limit />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="128" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { configApi } from '../../api';

const loading = ref(false);
const saving = ref(false);
const rows = ref([]);
const editVisible = ref(false);
const form = reactive({ cfgKey: '', cfgValue: '', remark: '' });

async function load() {
  loading.value = true;
  try {
    rows.value = (await configApi.list()) || [];
  } finally {
    loading.value = false;
  }
}

function openEdit(row) {
  form.cfgKey = row.cfgKey;
  form.cfgValue = row.cfgValue;
  form.remark = row.remark || '';
  editVisible.value = true;
}

async function save() {
  if (!form.cfgValue) return ElMessage.warning('配置值不能为空');
  saving.value = true;
  try {
    await configApi.update(form.cfgKey, { value: form.cfgValue, remark: form.remark });
    ElMessage.success('已保存');
    editVisible.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>
