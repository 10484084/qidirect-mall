<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">角色管理</span>
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
            <el-tag
              v-for="perm in row.perms || []"
              :key="perm"
              size="small"
              class="mr4"
              :type="perm === '*' ? 'success' : ''"
            >{{ permLabel(perm) }}</el-tag>
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
        <el-form-item label="全部权限">
          <el-checkbox v-model="allPerm" :indeterminate="indeterminate">超级管理员（拥有全部权限）</el-checkbox>
        </el-form-item>
        <template v-if="!allPerm">
          <el-form-item v-for="g in PERM_GROUPS" :key="g.title" :label="g.title">
            <el-checkbox-group v-model="picked">
              <el-checkbox
                v-for="item in g.items"
                :key="item.code"
                :value="item.code"
                :label="item.code"
              >{{ item.label }}</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
        </template>
        <el-collapse class="advanced">
          <el-collapse-item title="高级：手动编辑原始权限码" name="raw">
            <el-input
              v-model="form.permsText"
              type="textarea"
              :rows="4"
              placeholder="每行一个权限码，* 表示全部权限"
            />
            <div class="text-muted mt8">一般无需修改，上面的勾选会自动同步到这里。</div>
          </el-collapse-item>
        </el-collapse>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { roleApi } from '../../api';
import { PERM_GROUPS, permLabel } from '../../utils/perms';

const loading = ref(false);
const saving = ref(false);
const rows = ref([]);
const editVisible = ref(false);
const picked = ref([]);
const form = reactive({ id: null, name: '', permsText: '' });
const allPerm = ref(false);
const indeterminate = computed(() => !allPerm.value && picked.value.length > 0);

// 勾选变化时同步到高级文本域，保存仍统一从 picked + 文本域合并
watch(picked, (list) => {
  if (allPerm.value) return;
  form.permsText = list.join('\n');
}, { deep: true });

watch(allPerm, (v) => {
  if (v) {
    picked.value = [];
    form.permsText = '*';
  } else if (form.permsText === '*') {
    form.permsText = '';
  }
});

// 勾选变化时同步到高级文本域，保存仍统一从 picked + 文本域合并
watch(picked, (list) => {
  if (allPerm.value) return;
  form.permsText = list.join('\n');
}, { deep: true });

watch(allPerm, (v) => {
  if (v) {
    picked.value = [];
    form.permsText = '*';
  } else if (form.permsText === '*') {
    form.permsText = '';
  }
});

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
  const perms = (row && row.perms) || [];
  allPerm.value = perms.includes('*');
  picked.value = allPerm.value ? [] : [...perms];
  form.permsText = perms.join('\n');
  editVisible.value = true;
}

function collectPerms() {
  if (allPerm.value) return ['*'];
  const fromText = form.permsText
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
    .filter((item) => item !== '*');
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
