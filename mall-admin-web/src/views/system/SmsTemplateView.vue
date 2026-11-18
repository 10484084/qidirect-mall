<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">短信模板</span>
      <el-button type="primary" @click="openEdit()">新增模板</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="rows" border stripe v-loading="loading">
        <el-table-column prop="code" label="模板编码" width="200" />
        <el-table-column prop="providerId" label="服务商模板ID" width="200" />
        <el-table-column prop="content" label="模板内容" min-width="320" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑短信模板' : '新增短信模板'" width="560px">
      <el-form :model="form" label-width="130px">
        <el-form-item label="模板编码" required>
          <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 WITHDRAW_SUCCESS" />
        </el-form-item>
        <el-form-item label="服务商模板ID">
          <el-input v-model="form.providerId" placeholder="短信服务商下发的模板ID" />
        </el-form-item>
        <el-form-item label="模板内容" required>
          <el-input v-model="form.content" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" />
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
import { smsTemplateApi } from '../../api';

const loading = ref(false);
const saving = ref(false);
const rows = ref([]);
const editVisible = ref(false);
const form = reactive({ id: null, code: '', providerId: '', content: '', status: 1 });

async function load() {
  loading.value = true;
  try {
    rows.value = (await smsTemplateApi.list()) || [];
  } finally {
    loading.value = false;
  }
}

function openEdit(row) {
  form.id = row ? row.id : null;
  form.code = row ? row.code : '';
  form.providerId = row ? row.providerId : '';
  form.content = row ? row.content : '';
  form.status = row ? row.status ?? 1 : 1;
  editVisible.value = true;
}

async function save() {
  if (!form.code) return ElMessage.warning('请填写模板编码');
  if (!form.content) return ElMessage.warning('请填写模板内容');
  saving.value = true;
  const payload = { code: form.code, providerId: form.providerId, content: form.content, status: form.status };
  try {
    if (form.id) await smsTemplateApi.update(form.id, payload);
    else await smsTemplateApi.create(payload);
    ElMessage.success('已保存');
    editVisible.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>
