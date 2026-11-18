<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">通知发送</span>
      <el-button type="primary" @click="openSend">发送通知</el-button>
    </div>

    <el-card shadow="never">
      <div class="filter-bar">
        <StoreSelect v-model="query.targetId" placeholder="按门店筛选" style="width: 220px" @change="search" />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table :data="rows" border stripe v-loading="loading">
        <el-table-column label="通知对象" width="120">
          <template #default="{ row }">
            <el-tag type="info">{{ row.targetType === 1 ? '门店' : `类型${row.targetType}` }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetId" label="门店ID" width="100" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column prop="bizType" label="业务类型" width="120" />
        <el-table-column label="已读" width="80">
          <template #default="{ row }">
            <el-tag :type="row.readed === 1 ? 'success' : 'info'">{{ row.readed === 1 ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="发送时间" width="170" />
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

    <el-dialog v-model="sendVisible" title="发送通知" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="通知对象" required>
          <el-radio-group v-model="form.allStores">
            <el-radio :value="true">全部门店</el-radio>
            <el-radio :value="false">指定门店</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!form.allStores" label="门店" required>
          <div style="width: 100%">
            <el-select
              v-model="form.storeIds"
              multiple
              filterable
              remote
              :remote-method="searchStores"
              :loading="storeLoading"
              placeholder="输入手机号或门店名搜索"
              style="width: 100%"
            >
              <el-option v-for="item in storeOptions" :key="item.id" :label="`${item.name}（${item.phone}）`" :value="item.id" />
            </el-select>
          </div>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="form.content" type="textarea" :rows="5" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sendVisible = false">取消</el-button>
        <el-button type="primary" :loading="sending" @click="send">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import StoreSelect from '../../components/StoreSelect.vue';
import { noticeApi, storeApi } from '../../api';
import { toQuery } from '../../utils/format';

const loading = ref(false);
const sending = ref(false);
const rows = ref([]);
const total = ref(0);
const query = reactive({ targetId: null, page: 1, size: 20 });

const sendVisible = ref(false);
const storeLoading = ref(false);
const storeOptions = ref([]);
const form = reactive({ allStores: true, storeIds: [], title: '', content: '' });

async function load() {
  loading.value = true;
  try {
    const data = await noticeApi.page(toQuery(query));
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
  query.targetId = null;
  search();
}

async function searchStores(keyword) {
  if (!keyword) return;
  storeLoading.value = true;
  try {
    const data = await storeApi.page({ keyword, page: 1, size: 20 });
    storeOptions.value = data.records || [];
  } finally {
    storeLoading.value = false;
  }
}

function openSend() {
  form.allStores = true;
  form.storeIds = [];
  form.title = '';
  form.content = '';
  storeOptions.value = [];
  sendVisible.value = true;
}

async function send() {
  if (!form.title) return ElMessage.warning('请填写标题');
  if (!form.content) return ElMessage.warning('请填写内容');
  if (!form.allStores && !form.storeIds.length) return ElMessage.warning('请选择门店');
  await ElMessageBox.confirm(form.allStores ? '确认向全部门店发送该通知？' : '确认向选中门店发送该通知？', '发送确认', {
    type: 'warning'
  });
  sending.value = true;
  try {
    const count = await noticeApi.send({
      title: form.title,
      content: form.content,
      storeIds: form.allStores ? [] : form.storeIds,
      bizType: 'MANUAL'
    });
    ElMessage.success(`已发送 ${count} 条通知`);
    sendVisible.value = false;
    load();
  } finally {
    sending.value = false;
  }
}

onMounted(load);
</script>
