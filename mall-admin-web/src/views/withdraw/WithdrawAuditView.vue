<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">提现审核</span>
      <div>
        <el-button :disabled="!selection.length" type="success" @click="createBatch">
          生成打款批次{{ selection.length ? `（${selection.length}）` : '' }}
        </el-button>
        <el-button type="primary" @click="load">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <div class="filter-bar">
        <StoreSelect v-model="query.storeId" placeholder="门店" style="width: 220px" @change="search" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="search">
          <el-option v-for="item in WITHDRAW_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input v-model.number="query.batchId" placeholder="批次ID" clearable style="width: 140px" @keyup.enter="search" />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table :data="rows" border stripe v-loading="loading" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="48" :selectable="(row) => row.status === 1 && !row.batchId" />
        <el-table-column prop="storeName" label="门店" min-width="140" show-overflow-tooltip />
        <el-table-column prop="storePhone" label="手机号" width="130" />
        <el-table-column label="提现金额" width="120">
          <template #default="{ row }"><span class="money">{{ fen2yuan(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="收款卡" min-width="220">
          <template #default="{ row }">
            {{ row.bankHolder }} / {{ row.bankName }} / {{ row.bankAccountMasked }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta(WITHDRAW_STATUS, row.status).type">
              {{ statusMeta(WITHDRAW_STATUS, row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="批次" width="170">
          <template #default="{ row }">
            <span v-if="row.batchNo">{{ row.batchNo }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="applyTime" label="申请时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" link type="primary" @click="approve(row)">通过</el-button>
            <el-button v-if="row.status === 0" link type="danger" @click="openReason(row, 'reject')">驳回</el-button>
            <el-button v-if="row.status === 1" link type="success" @click="openPaid(row)">打款成功</el-button>
            <el-button v-if="row.status === 1" link type="danger" @click="openReason(row, 'fail')">打款失败</el-button>
            <el-button link @click="openDetail(row)">详情</el-button>
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

    <el-dialog v-model="reasonVisible" :title="reasonType === 'reject' ? '驳回提现' : '标记打款失败'" width="440px">
      <el-form label-width="90px">
        <el-form-item label="原因" required>
          <el-input v-model="reason" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
        <el-alert
          v-if="reasonType === 'fail'"
          type="warning"
          :closable="false"
          title="标记失败后提现金额将解冻回门店佣金余额"
        />
      </el-form>
      <template #footer>
        <el-button @click="reasonVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReason">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="paidVisible" title="标记打款成功" width="480px">
      <el-form label-width="90px">
        <el-form-item label="打款凭证" required>
          <ImageUploader v-model="paidProofs" :limit="5" tip="支持 jpg/png/webp/pdf，单张不超过 5MB" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="paidVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitPaid">确认打款</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="提现详情" width="620px">
      <el-descriptions v-if="detailRow" :column="2" border>
        <el-descriptions-item label="门店">{{ detailRow.storeName }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detailRow.storePhone }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ fen2yuan(detailRow.amount) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ statusMeta(WITHDRAW_STATUS, detailRow.status).label }}
        </el-descriptions-item>
        <el-descriptions-item label="持卡人">{{ detailRow.bankHolder }}</el-descriptions-item>
        <el-descriptions-item label="开户行">{{ detailRow.bankName }}</el-descriptions-item>
        <el-descriptions-item label="卡号" :span="2">{{ detailRow.bankAccountMasked }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ detailRow.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ detailRow.applyTime }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ detailRow.auditTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="打款时间">{{ detailRow.paidTime || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="detailRow.rejectReason" label="驳回原因" :span="2">
          {{ detailRow.rejectReason }}
        </el-descriptions-item>
        <el-descriptions-item v-if="detailRow.failReason" label="失败原因" :span="2">
          {{ detailRow.failReason }}
        </el-descriptions-item>
        <el-descriptions-item v-if="detailRow.proofImages && detailRow.proofImages.length" label="打款凭证" :span="2">
          <div class="image-list">
            <a v-for="url in detailRow.proofImages" :key="url" :href="url" target="_blank">
              <img :src="url" class="thumb" />
            </a>
          </div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import StoreSelect from '../../components/StoreSelect.vue';
import ImageUploader from '../../components/ImageUploader.vue';
import { withdrawApi } from '../../api';
import { WITHDRAW_STATUS, WITHDRAW_STATUS_OPTIONS, statusMeta } from '../../utils/dict';
import { fen2yuan, toQuery } from '../../utils/format';

const loading = ref(false);
const saving = ref(false);
const rows = ref([]);
const total = ref(0);
const selection = ref([]);
const query = reactive({ storeId: null, status: null, batchId: '', page: 1, size: 20 });

const reasonVisible = ref(false);
const reasonType = ref('reject');
const reason = ref('');
const currentRow = ref(null);

const paidVisible = ref(false);
const paidProofs = ref([]);

const detailVisible = ref(false);
const detailRow = ref(null);

async function load() {
  loading.value = true;
  try {
    const data = await withdrawApi.page(toQuery(query));
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
  query.storeId = null;
  query.status = null;
  query.batchId = '';
  search();
}

function onSelectionChange(list) {
  selection.value = list;
}

async function approve(row) {
  await ElMessageBox.confirm(`确认通过门店「${row.storeName}」的提现 ${fen2yuan(row.amount)} 元？`, '审核', {
    type: 'warning'
  });
  await withdrawApi.approve(row.id);
  ElMessage.success('已通过审核');
  load();
}

function openReason(row, type) {
  currentRow.value = row;
  reasonType.value = type;
  reason.value = '';
  reasonVisible.value = true;
}

async function submitReason() {
  if (!reason.value) return ElMessage.warning('请填写原因');
  saving.value = true;
  try {
    if (reasonType.value === 'reject') await withdrawApi.reject(currentRow.value.id, reason.value);
    else await withdrawApi.fail(currentRow.value.id, reason.value);
    ElMessage.success('已提交');
    reasonVisible.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

function openPaid(row) {
  currentRow.value = row;
  paidProofs.value = [];
  paidVisible.value = true;
}

async function submitPaid() {
  if (!paidProofs.value.length) return ElMessage.warning('请上传打款凭证');
  saving.value = true;
  try {
    await withdrawApi.paid(currentRow.value.id, paidProofs.value);
    ElMessage.success('已标记打款成功');
    paidVisible.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

function openDetail(row) {
  detailRow.value = row;
  detailVisible.value = true;
}

async function createBatch() {
  const ids = selection.value.map((item) => item.id);
  await ElMessageBox.confirm(`确认将选中的 ${ids.length} 笔提现生成打款批次？`, '生成批次', { type: 'warning' });
  const batch = await withdrawApi.createBatch(ids);
  ElMessage.success(`已生成批次 ${batch.batchNo}，共 ${batch.count} 笔`);
  load();
}

onMounted(load);
</script>
