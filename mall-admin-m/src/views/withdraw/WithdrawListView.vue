<script setup>
import { onActivated, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant';
import { withdrawApi } from '../../api';
import { uploadFile } from '../../api/request';
import { WITHDRAW_STATUS } from '../../utils/dict';
import { fen2yuan } from '../../utils/format';

defineOptions({ name: 'WithdrawListView' });

const route = useRoute();

const TABS = [
  { name: 'all', title: '全部' },
  { name: '0', title: '待审核' },
  { name: '1', title: '待打款' },
  { name: '2', title: '已打款' },
  { name: '3', title: '已驳回' }
];

const initStatus = route.query.status !== undefined && route.query.status !== '' ? String(route.query.status) : 'all';
const activeTab = ref(TABS.some((t) => t.name === initStatus) ? initStatus : 'all');

const rows = ref([]);
const page = ref(1);
const size = 20;
const loading = ref(false);
const finished = ref(false);
const refreshing = ref(false);

const reasonVisible = ref(false);
const reasonTitle = ref('');
const reasonText = ref('');
const reasonType = ref('reject');
const current = ref(null);

const paidVisible = ref(false);
const fileList = ref([]);
const proofImages = ref([]);
const submitting = ref(false);
const uploading = ref(false);

watch(
  fileList,
  (list) => {
    proofImages.value = list.map((f) => f.url).filter(Boolean);
  },
  { deep: true }
);

function statusTag(status) {
  const meta = WITHDRAW_STATUS[status] || { label: '-', type: 'info' };
  const cls = { warning: 'tag-warn', primary: '', success: 'tag-success', danger: 'tag-danger' };
  return { label: meta.label, cls: cls[meta.type] ?? 'tag-gray' };
}

async function fetchPage() {
  const params = { page: page.value, size };
  if (activeTab.value !== 'all') params.status = Number(activeTab.value);
  const res = await withdrawApi.page(params);
  const records = (res && res.records) || [];
  rows.value = page.value === 1 ? records : rows.value.concat(records);
  const total = (res && res.total) || 0;
  if (rows.value.length >= total || records.length === 0) {
    finished.value = true;
  }
  page.value += 1;
}

async function resetList() {
  page.value = 1;
  finished.value = false;
  rows.value = [];
  loading.value = true;
  try {
    await fetchPage();
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

async function onLoad() {
  if (finished.value) {
    loading.value = false;
    return;
  }
  try {
    await fetchPage();
  } finally {
    loading.value = false;
  }
}

function onTabChange() {
  resetList();
}

async function approve(row) {
  try {
    await showConfirmDialog({
      title: '通过提现',
      message: `确认通过「${row.storeName}」的 ¥${fen2yuan(row.amount)} 提现申请？`
    });
  } catch (e) {
    return;
  }
  await withdrawApi.approve(row.id);
  showSuccessToast('已通过');
  resetList();
}

function openReason(row, type) {
  current.value = row;
  reasonType.value = type;
  reasonTitle.value = type === 'reject' ? '驳回提现' : '标记打款失败';
  reasonText.value = '';
  reasonVisible.value = true;
}

async function submitReason() {
  if (!reasonText.value.trim()) {
    showFailToast('请填写原因');
    return;
  }
  submitting.value = true;
  try {
    if (reasonType.value === 'reject') {
      await withdrawApi.reject(current.value.id, reasonText.value.trim());
      showSuccessToast('已驳回');
    } else {
      await withdrawApi.fail(current.value.id, reasonText.value.trim());
      showSuccessToast('已标记失败');
    }
    reasonVisible.value = false;
    resetList();
  } finally {
    submitting.value = false;
  }
}

function openPaid(row) {
  current.value = row;
  fileList.value = [];
  paidVisible.value = true;
}

async function onAfterRead(item) {
  const items = Array.isArray(item) ? item : [item];
  uploading.value = true;
  try {
    for (const it of items) {
      it.status = 'uploading';
      it.message = '上传中';
      try {
        it.url = await uploadFile(it.file);
        it.status = 'done';
        it.message = '';
      } catch (e) {
        it.status = 'failed';
        it.message = '上传失败';
      }
    }
  } finally {
    uploading.value = false;
  }
}

async function submitPaid() {
  if (uploading.value) {
    showFailToast('图片上传中，请稍候');
    return;
  }
  if (!proofImages.value.length) {
    showFailToast('请上传打款凭证');
    return;
  }
  submitting.value = true;
  try {
    await withdrawApi.paid(current.value.id, proofImages.value);
    showSuccessToast('已标记打款成功');
    paidVisible.value = false;
    resetList();
  } finally {
    submitting.value = false;
  }
}

onMounted(resetList);

let firstActivate = true;
onActivated(() => {
  if (firstActivate) {
    firstActivate = false;
    return;
  }
  resetList();
});
</script>

<template>
  <div class="page">
    <div class="sticky-bar">
      <van-tabs v-model:active="activeTab" shrink color="#1f6feb" title-active-color="#1f6feb" @change="onTabChange">
        <van-tab v-for="t in TABS" :key="t.name" :name="t.name" :title="t.title" />
      </van-tabs>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="resetList">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        :immediate-check="false"
        @load="onLoad"
      >
        <div v-for="row in rows" :key="row.id" class="card">
          <div class="row">
            <span class="store ellipsis">{{ row.storeName || '-' }}</span>
            <span class="tag-text" :class="statusTag(row.status).cls">{{ statusTag(row.status).label }}</span>
          </div>

          <div class="row">
            <span class="amount money">¥{{ fen2yuan(row.amount) }}</span>
            <span class="muted">{{ row.applyTime }}</span>
          </div>

          <div class="row">
            <span class="muted ellipsis">
              {{ row.bankHolder }} · {{ row.bankName }} · {{ row.bankAccountMasked }}
            </span>
          </div>

          <div v-if="row.batchNo" class="row">
            <span class="muted">批次 {{ row.batchNo }}</span>
          </div>

          <div v-if="row.status === 0 || row.status === 1" class="ops">
            <template v-if="row.status === 0">
              <van-button size="small" type="primary" @click="approve(row)">通过</van-button>
              <van-button size="small" plain type="danger" @click="openReason(row, 'reject')">驳回</van-button>
            </template>
            <template v-else>
              <van-button size="small" type="success" @click="openPaid(row)">打款成功</van-button>
              <van-button size="small" plain type="danger" @click="openReason(row, 'fail')">打款失败</van-button>
            </template>
          </div>
        </div>

        <div v-if="!loading && !rows.length" class="empty-tip">暂无提现申请</div>
      </van-list>
    </van-pull-refresh>

    <van-popup v-model:show="reasonVisible" position="bottom" round safe-area-inset-bottom>
      <div class="sheet">
        <div class="sheet-title">{{ reasonTitle }}</div>
        <van-cell-group inset>
          <van-field
            v-model="reasonText"
            label="原因"
            type="textarea"
            rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请填写原因"
          />
        </van-cell-group>
        <div class="sheet-actions">
          <van-button plain block round @click="reasonVisible = false">取消</van-button>
          <van-button type="primary" block round :loading="submitting" @click="submitReason">确认</van-button>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="paidVisible" position="bottom" round safe-area-inset-bottom>
      <div class="sheet">
        <div class="sheet-title">标记打款成功</div>
        <van-cell-group inset>
          <van-field label="打款凭证">
            <template #input>
              <van-uploader v-model="fileList" :max-count="5" accept="image/*" :after-read="onAfterRead" />
            </template>
          </van-field>
        </van-cell-group>
        <div class="sheet-actions">
          <van-button plain block round @click="paidVisible = false">取消</van-button>
          <van-button type="primary" block round :loading="submitting" @click="submitPaid">确认打款</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.sticky-bar {
  margin: -12px -12px 8px;
  padding: 0;
  background: #fff;
}

.store {
  font-size: 15px;
  font-weight: 600;
  max-width: 66%;
}

.amount {
  font-size: 18px;
}

.ops {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #f2f4f7;
  justify-content: flex-end;
}

.sheet {
  padding: 18px 0 16px;
}

.sheet-title {
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 14px;
}

.sheet-actions {
  display: flex;
  gap: 10px;
  padding: 18px 16px 0;
}
</style>
