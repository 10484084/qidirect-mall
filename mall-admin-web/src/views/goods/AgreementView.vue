<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { agreementApi } from '../../api';

const loading = ref(false);
const rows = ref([]);
const dialogVisible = ref(false);
const editing = ref(null);
const formRef = ref();
const form = reactive({ title: '', version: '', content: '', status: 1 });

const previewVisible = ref(false);
const preview = ref(null);

async function load() {
  loading.value = true;
  try {
    rows.value = (await agreementApi.list()) || [];
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editing.value = null;
  Object.assign(form, { title: '', version: '', content: '', status: 1 });
  dialogVisible.value = true;
}

function openEdit(row) {
  editing.value = row;
  Object.assign(form, {
    title: row.title,
    version: row.version,
    content: row.content,
    status: row.status ?? 1
  });
  dialogVisible.value = true;
}

async function submit() {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  if (editing.value) {
    await agreementApi.update(editing.value.id, { ...form });
  } else {
    await agreementApi.create({ ...form });
  }
  ElMessage.success('已保存');
  dialogVisible.value = false;
  load();
}

async function toggle(row) {
  await agreementApi.changeStatus(row.id, row.status === 1 ? 0 : 1);
  ElMessage.success('状态已更新');
  load();
}

function showPreview(row) {
  preview.value = row;
  previewVisible.value = true;
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">协议管理</h2>
      <div>
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="openCreate">新增协议</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="version" label="版本" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '生效中' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showPreview(row)">预览</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="toggle(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑协议' : '新增协议'" width="620px" top="6vh">
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-form-item label="标题" prop="title" :rules="[{ required: true, message: '请输入标题' }]">
          <el-input v-model="form.title" maxlength="64" />
        </el-form-item>
        <el-form-item label="版本号" prop="version" :rules="[{ required: true, message: '请输入版本号' }]">
          <el-input v-model="form.version" maxlength="16" placeholder="如 v1.0" />
        </el-form-item>
        <el-form-item label="协议内容" prop="content" :rules="[{ required: true, message: '请输入协议内容' }]">
          <el-input v-model="form.content" type="textarea" :rows="12" />
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

    <el-dialog v-model="previewVisible" :title="preview ? preview.title + ' ' + preview.version : '预览'" width="640px">
      <div v-if="preview" class="pre-wrap">{{ preview.content }}</div>
    </el-dialog>
  </div>
</template>
