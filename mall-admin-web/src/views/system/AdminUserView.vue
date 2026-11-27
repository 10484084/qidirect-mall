<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">账号管理</span>
      <el-button type="primary" @click="openEdit()">新增管理员</el-button>
    </div>

    <el-card shadow="never">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="账号或姓名" clearable style="width: 200px" @keyup.enter="search" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="search">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table :data="rows" border stripe v-loading="loading">
        <el-table-column prop="account" label="账号" width="160" />
        <el-table-column prop="realName" label="姓名" width="130" />
        <el-table-column label="手机号" width="140">
          <template #default="{ row }">
            <span>{{ row.phone || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="name in row.roleNames || []" :key="name" class="mr4">{{ name }}</el-tag>
            <span v-if="!(row.roleNames || []).length" class="text-muted">未分配</span>
          </template>
        </el-table-column>
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

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </el-card>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑管理员' : '新增管理员'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="账号" required>
          <el-input v-model="form.account" :disabled="!!form.id" maxlength="32" />
        </el-form-item>
        <el-form-item :label="form.id ? '重置密码' : '密码'" :required="!form.id">
          <el-input v-model="form.password" type="password" show-password maxlength="64"
                    :placeholder="form.id ? '留空表示不修改' : '请输入初始密码'" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" maxlength="32" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" maxlength="11" placeholder="用于忘记密码时自助重置" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple placeholder="选择角色" style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
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
import { adminUserApi, roleApi } from '../../api';
import { toQuery } from '../../utils/format';

const loading = ref(false);
const saving = ref(false);
const rows = ref([]);
const total = ref(0);
const roles = ref([]);
const query = reactive({ keyword: '', status: null, page: 1, size: 20 });

const editVisible = ref(false);
const form = reactive({ id: null, account: '', password: '', realName: '', phone: '', roleIds: [], status: 1 });

async function load() {
  loading.value = true;
  try {
    const data = await adminUserApi.page(toQuery(query));
    rows.value = data.records || [];
    total.value = data.total || 0;
  } finally {
    loading.value = false;
  }
}

function search() {
  query.page = 1;
  load();
}

function reset() {
  query.keyword = '';
  query.status = null;
  search();
}

async function openEdit(row) {
  roles.value = (await roleApi.list()) || [];
  form.id = row ? row.id : null;
  form.account = row ? row.account : '';
  form.password = '';
  form.realName = row ? row.realName || '' : '';
  form.phone = row ? row.phone || '' : '';
  form.roleIds = row && row.roleIds ? [...row.roleIds] : [];
  form.status = row ? row.status ?? 1 : 1;
  editVisible.value = true;
}

async function save() {
  if (!form.account) return ElMessage.warning('请填写账号');
  if (!form.id && !form.password) return ElMessage.warning('请填写初始密码');
  saving.value = true;
  try {
    if (form.id) {
      await adminUserApi.update(form.id, {
        password: form.password || null,
        realName: form.realName,
        phone: form.phone || '',
        status: form.status,
        roleIds: form.roleIds
      });
    } else {
      await adminUserApi.create({
        account: form.account,
        password: form.password,
        realName: form.realName,
        phone: form.phone || '',
        status: form.status,
        roleIds: form.roleIds
      });
    }
    ElMessage.success('已保存');
    editVisible.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>
