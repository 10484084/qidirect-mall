<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">角色权限</span>
      <el-button type="primary" @click="openEdit()">新增角色</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="rows" border stripe v-loading="loading">
        <el-table-column prop="name" label="角色名称" width="200" />
        <el-table-column label="管理员数" width="120">
          <template #default="{ row }">{{ row.userCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="权限点" min-width="360">
          <template #default="{ row }">
            <el-tag v-for="perm in row.perms || []" :key="perm" size="small" class="mr4">{{ perm }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑角色' : '新增角色'" width="600px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="角色名称" required>
          <el-input v-model="form.name" maxlength="32" />
        </el-form-item>
        <el-form-item label="权限点" required>
          <el-input
            v-model="form.permsText"
            type="textarea"
            :rows="5"
            placeholder="每行一个权限点，可手动输入；* 表示全部权限"
          />
        </el-form-item>
        <el-form-item label="常用权限">
          <el-checkbox-group v-model="picked">
            <el-checkbox v-for="perm in COMMON_PERMS" :key="perm" :value="perm" :label="perm" />
          </el-checkbox-group>
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
import { roleApi } from '../../api';

const COMMON_PERMS = ['*', 'store:list', 'store:detail', 'store:freeze', 'order:list', 'order:ship', 'withdraw:audit', 'withdraw:export'];

const loading = ref(false);
const saving = ref(false);
const rows = ref([]);
const editVisible = ref(false);
const picked = ref([]);
const form = reactive({ id: null, name: '', permsText: '' });

async function load() {
  loading.value = true;
  try {
    rows.value = (await roleApi.list()) || [];
  } finally {
    loading.value = false;
  }
}

function openEdit(row) {
  form.id = row ? row.id : null;
  form.name = row ? row.name : '';
  form.permsText = row && row.perms ? row.perms.join('\n') : '';
  picked.value = row && row.perms ? [...row.perms] : [];
  editVisible.value = true;
}

function collectPerms() {
  const fromText = form.permsText
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean);
  return Array.from(new Set([...fromText, ...picked.value]));
}

async function save() {
  if (!form.name) return ElMessage.warning('请填写角色名称');
  const perms = collectPerms();
  if (!perms.length) return ElMessage.warning('至少填写一个权限点');
  saving.value = true;
  try {
    if (form.id) await roleApi.update(form.id, { name: form.name, perms });
    else await roleApi.create({ name: form.name, perms });
    ElMessage.success('已保存，重新登录后生效');
    editVisible.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>
