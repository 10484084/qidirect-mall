<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { cancelAuditApi } from '../../api';
import { fen2yuan } from '../../utils/format';

const loading = ref(false);
const rows = ref([]);
const total = ref(0);
const query = reactive({ status: 0, page: 1, size: 20 });

const STATUS = {
  0: { text: '待审核', type: 'warning' },
  1: { text: '已通过', type: 'success' },
  2: { text: '已驳回', type: 'info' },
  3: { text: '退款失败', type: 'danger' },
  4: { text: '处理中', type: 'primary' }
};

async function load() {
  loading.value = true;
  try {
    const params = { page: query.page, size: query.size };
    if (query.status !== '' && query.status !== null) params.status = query.status;
    const res = await cancelAuditApi.page(params);
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

async function audit(row, approved) {
  let remark = '';
  if (approved) {
    try {
      await ElMessageBox.confirm(
        `确认通过后：预存部分退回门店余额 ¥${fen2yuan(row.depositRefundHint || 0)}，在线支付部分原路退回。是否继续？`,
        '通过取消申请',
        { type: 'warning', confirmButtonText: '确认退款' }
      );
    } catch (e) {
      return;
    }
  } else {
    try {
      const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回取消申请', {
        inputPlaceholder: '如：商品已备货完成',
        inputValidator: (v) => (v && v.trim() ? true : '请填写原因')
      });
      remark = value || '';
    } catch (e) {
      return;
    }
  }
  await cancelAuditApi.audit(row.id, { approved, remark });
  ElMessage.success(approved ? '已通过并发起退款' : '已驳回');
  load();
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">取消审核</h2>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-card shadow="never">
      <el-form class="filter-bar" inline>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
            <el-option label="待审核" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已驳回" :value="2" />
            <el-option label="退款失败" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="orderNo" label="订单号" min-width="180" />
        <el-table-column label="门店ID" width="90" prop="storeId" />
        <el-table-column prop="reason" label="申请原因" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="(STATUS[row.status] || {}).type || 'info'" size="small">
              {{ (STATUS[row.status] || {}).text || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="退款金额" width="160">
          <template #default="{ row }">
            <div v-if="row.status === 1 || row.status === 4">
              预存 ¥{{ fen2yuan(row.depositRefund) }} / 原路 ¥{{ fen2yuan(row.onlineRefund) }}
            </div>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="refundMsg" label="退款说明" min-width="150" show-overflow-tooltip />
        <el-table-column label="申请时间" width="170">
          <template #default="{ row }">{{ (row.createdAt || '').replace('T', ' ').slice(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0 || row.status === 3">
              <el-button link type="primary" @click="audit(row, true)">通过退款</el-button>
              <el-button link type="danger" @click="audit(row, false)">驳回</el-button>
            </template>
            <span v-else class="text-muted">已处理</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="load"
          @size-change="search"
        />
      </div>
    </el-card>
  </div>
</template>
