<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">打款批次</span>
      <el-button type="primary" @click="load">刷新</el-button>
    </div>

    <el-card shadow="never">
      <div class="filter-bar">
        <el-select v-model="query.status" placeholder="批次状态" clearable style="width: 160px" @change="search">
          <el-option label="待打款" :value="0" />
          <el-option label="已打款" :value="1" />
          <el-option label="已结束" :value="2" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
      </div>

      <el-table :data="rows" border stripe v-loading="loading">
        <el-table-column prop="batchNo" label="批次号" min-width="190" />
        <el-table-column label="笔数" width="90">
          <template #default="{ row }">{{ row.count }}</template>
        </el-table-column>
        <el-table-column label="批次金额" width="130">
          <template #default="{ row }"><span class="money">{{ fen2yuan(row.totalAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="失败笔数/金额" width="150">
          <template #default="{ row }">
            <span class="text-danger">{{ row.failCount || 0 }} 笔 / {{ fen2yuan(row.failAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="BATCH_STATUS[row.status]?.type || 'info'">{{ BATCH_STATUS[row.status]?.label || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column prop="paidAt" label="完成时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openBatch(row)">查看明细</el-button>
            <el-button link type="success" @click="exportBatch(row)">导出 CSV</el-button>
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

    <el-drawer v-model="drawerVisible" :title="`批次明细 ${current?.batchNo || ''}`" size="64%">
      <el-descriptions v-if="current" :column="3" border>
        <el-descriptions-item label="批次号">{{ current.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="笔数">{{ current.count }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ fen2yuan(current.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ BATCH_STATUS[current.status]?.label || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ current.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ current.paidAt || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="items" border stripe class="mt16" v-loading="itemLoading">
        <el-table-column prop="storeName" label="门店" min-width="130" show-overflow-tooltip />
        <el-table-column label="金额" width="110">
          <template #default="{ row }"><span class="money">{{ fen2yuan(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="收款卡" min-width="220">
          <template #default="{ row }">{{ row.bankHolder }} / {{ row.bankName }} / {{ row.bankAccountMasked }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta(WITHDRAW_STATUS, row.status).type">
              {{ statusMeta(WITHDRAW_STATUS, row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="150">
          <template #default="{ row }">{{ row.failReason || row.rejectReason || '-' }}</template>
        </el-table-column>
        <el-table-column prop="paidTime" label="打款时间" width="170" />
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="itemQuery.page"
          :page-size="itemQuery.size"
          :total="itemTotal"
          layout="total, prev, pager, next"
          @current-change="loadItems"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { withdrawApi } from '../../api';
import { WITHDRAW_STATUS, statusMeta } from '../../utils/dict';
import { fen2yuan, toQuery } from '../../utils/format';

const BATCH_STATUS = {
  0: { label: '待打款', type: 'primary' },
  1: { label: '已打款', type: 'success' },
  2: { label: '已结束', type: 'info' }
};

const loading = ref(false);
const rows = ref([]);
const total = ref(0);
const query = reactive({ status: null, page: 1, size: 20 });

const drawerVisible = ref(false);
const current = ref(null);
const items = ref([]);
const itemTotal = ref(0);
const itemLoading = ref(false);
const itemQuery = reactive({ batchId: null, page: 1, size: 20 });

async function load() {
  loading.value = true;
  try {
    const data = await withdrawApi.batches(toQuery(query));
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

async function loadItems() {
  itemLoading.value = true;
  try {
    const data = await withdrawApi.page({ batchId: itemQuery.batchId, page: itemQuery.page, size: itemQuery.size });
    items.value = data.records || [];
    itemTotal.value = data.total || 0;
  } finally {
    itemLoading.value = false;
  }
}

async function openBatch(row) {
  current.value = row;
  itemQuery.batchId = row.id;
  itemQuery.page = 1;
  drawerVisible.value = true;
  await loadItems();
}

async function exportBatch(row) {
  await withdrawApi.exportBatch(row.id);
  ElMessage.success('导出已开始');
}

onMounted(load);
</script>
