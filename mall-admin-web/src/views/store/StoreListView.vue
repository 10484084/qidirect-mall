<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { levelApi, storeApi } from '../../api';
import StoreSelect from '../../components/StoreSelect.vue';
import { AUTH_STATUS, AUTH_STATUS_OPTIONS, STORE_STATUS, STORE_STATUS_OPTIONS, statusMeta } from '../../utils/dict';
import { fen2yuan } from '../../utils/format';
import StoreDetailDrawer from './StoreDetailDrawer.vue';

const loading = ref(false);
const rows = ref([]);
const total = ref(0);
const levels = ref([]);
const drawer = ref();

const query = reactive({
  keyword: '',
  levelId: null,
  status: null,
  authStatus: null,
  duplicateFlag: null,
  parentId: null,
  page: 1,
  size: 20
});

const createVisible = ref(false);
const createForm = reactive({ realName: '', phone: '', name: '', parentId: null, remark: '' });
const createFormRef = ref();

async function load() {
  loading.value = true;
  try {
    const params = {};
    Object.entries(query).forEach(([k, v]) => {
      if (v !== '' && v !== null && v !== undefined) params[k] = v;
    });
    const res = await storeApi.page(params);
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

function reset() {
  Object.assign(query, {
    keyword: '',
    levelId: null,
    status: null,
    authStatus: null,
    duplicateFlag: null,
    parentId: null,
    page: 1
  });
  load();
}

async function submitCreate() {
  if (!createFormRef.value) return;
  const valid = await createFormRef.value.validate().catch(() => false);
  if (!valid) return;
  await storeApi.create({ ...createForm });
  ElMessage.success('门店已创建');
  createVisible.value = false;
  Object.assign(createForm, { realName: '', phone: '', name: '', parentId: null, remark: '' });
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
      <h2 class="page-title">门店列表</h2>
      <el-button type="primary" @click="createVisible = true">新增门店</el-button>
    </div>

    <el-card shadow="never">
      <el-form class="filter-bar" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="名称/昵称/手机号/邀请码"
            clearable
            style="width: 220px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="档位">
          <el-select v-model="query.levelId" clearable placeholder="全部" style="width: 140px">
            <el-option v-for="l in levels" :key="l.id" :label="l.name" :value="l.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 110px">
            <el-option v-for="o in STORE_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="实名">
          <el-select v-model="query.authStatus" clearable placeholder="全部" style="width: 110px">
            <el-option v-for="o in AUTH_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="风控">
          <el-select v-model="query.duplicateFlag" clearable placeholder="全部" style="width: 120px">
            <el-option label="疑似重复" :value="1" />
            <el-option label="正常" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="门店" min-width="160">
          <template #default="{ row }">
            <div>{{ row.name || row.nickname || '未命名' }}</div>
            <div class="text-muted">{{ row.phone }}</div>
          </template>
        </el-table-column>
        <el-table-column label="上级" min-width="120">
          <template #default="{ row }">{{ row.parentName || '-' }}</template>
        </el-table-column>
        <el-table-column label="档位" min-width="130">
          <template #default="{ row }">
            <div>{{ row.levelName || '未开通' }}</div>
            <div v-if="row.levelPaidAmount" class="text-muted">¥{{ fen2yuan(row.levelPaidAmount) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusMeta(STORE_STATUS, row.status).type" size="small">
              {{ statusMeta(STORE_STATUS, row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="实名" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta(AUTH_STATUS, row.authStatus).type" size="small">
              {{ statusMeta(AUTH_STATUS, row.authStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="风控" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.duplicateFlag" type="danger" size="small">疑似重复</el-tag>
            <span v-else class="text-muted">正常</span>
          </template>
        </el-table-column>
        <el-table-column prop="joinTime" label="加入时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="drawer.open(row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          @current-change="load"
          @size-change="search"
        />
      </div>
    </el-card>

    <StoreDetailDrawer ref="drawer" />

    <el-dialog v-model="createVisible" title="新增门店" width="500px">
      <el-form ref="createFormRef" :model="createForm" label-width="90px">
        <el-form-item label="姓名" prop="realName" :rules="[{ max: 32, message: '姓名过长' }]">
          <el-input v-model="createForm.realName" maxlength="32" placeholder="请输入门店负责人姓名" />
        </el-form-item>
        <el-form-item
          label="手机号"
          prop="phone"
          :rules="[
            { required: true, message: '请输入手机号' },
            { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }
          ]"
        >
          <el-input v-model="createForm.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="门店名称">
          <el-input v-model="createForm.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="上级门店">
          <StoreSelect v-model="createForm.parentId" placeholder="不选则为顶级门店" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
