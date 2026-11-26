<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">异常单处置</span>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-radio-group v-model="type" @change="search">
          <el-radio-button v-for="item in ABNORMAL_TYPE_OPTIONS" :key="item.value" :value="item.value">
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
        <el-button @click="load">刷新</el-button>
        <span class="toolbar-hint">异常单 = 已收款但业务前置校验失败的订单，需人工处置</span>
      </div>

      <el-table :data="rows" border stripe v-loading="loading">
        <el-table-column prop="orderNo" label="订单号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="storeName" label="门店" min-width="140" show-overflow-tooltip />
        <el-table-column prop="levelName" label="目标档位" width="120" />
        <el-table-column label="金额" width="120">
          <template #default="{ row }"><span class="money">{{ fen2yuan(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column prop="payNo" label="支付单号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="paidAt" label="支付时间" width="170" />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openResolve(row)">处置</el-button>
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

    <el-dialog v-model="resolveVisible" title="异常单处置" width="520px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="订单号">{{ current.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="门店">{{ current.storeName }}</el-descriptions-item>
        <el-descriptions-item label="档位">{{ current.levelName }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ fen2yuan(current.amount) }}</el-descriptions-item>
      </el-descriptions>

      <el-form :model="form" label-width="100px" class="mt16">
        <el-form-item label="处置方式" required>
          <el-radio-group v-model="form.action">
            <el-radio v-for="item in ABNORMAL_RESOLVE_OPTIONS" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="处置凭证">
          <ImageUploader v-model="form.proofImages" :limit="5" />
        </el-form-item>
        <el-alert
          v-if="form.action === 'REFUND'"
          type="warning"
          :closable="false"
          title="退款将调用支付渠道原路退回，请确认支付单状态"
        />
      </el-form>

      <template #footer>
        <el-button @click="resolveVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确认处置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import ImageUploader from '../../components/ImageUploader.vue';
import { financeApi } from '../../api';
import { ABNORMAL_RESOLVE, ABNORMAL_RESOLVE_OPTIONS, ABNORMAL_TYPE, ABNORMAL_TYPE_OPTIONS } from '../../utils/dict';
import { fen2yuan } from '../../utils/format';

const type = ref('JOIN');
const loading = ref(false);
const saving = ref(false);
const rows = ref([]);
const total = ref(0);
const query = reactive({ page: 1, size: 20 });

const resolveVisible = ref(false);
const current = ref(null);
const form = reactive({ action: 'REOPEN', remark: '', proofImages: [] });

async function load() {
  loading.value = true;
  try {
    const data = await financeApi.abnormalOrders({ type: type.value, page: query.page, size: query.size });
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

function openResolve(row) {
  current.value = row;
  form.action = 'REOPEN';
  form.remark = '';
  form.proofImages = [];
  resolveVisible.value = true;
}

async function submit() {
  await ElMessageBox.confirm(`确认执行「${ABNORMAL_RESOLVE[form.action]}」？`, '提示', { type: 'warning' });
  saving.value = true;
  try {
    // 升级单在列表中归入 JOIN，与后端接口类型保持一致
    await financeApi.resolveAbnormal(type.value, current.value.orderId, {
      action: form.action,
      remark: form.remark,
      proofImages: form.proofImages
    });
    ElMessage.success('处置完成');
    resolveVisible.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid #f0f2f5;
}

.toolbar-hint {
  margin-left: auto;
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}
</style>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid #f0f2f5;
}

.toolbar-hint {
  margin-left: auto;
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}
</style>
