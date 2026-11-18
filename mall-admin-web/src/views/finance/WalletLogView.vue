<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">余额流水</span>
    </div>

    <el-card shadow="never">
      <div class="filter-bar">
        <StoreSelect v-model="query.storeId" placeholder="门店" style="width: 220px" @change="search" />
        <el-select v-model="query.account" placeholder="账户" clearable style="width: 140px" @change="search">
          <el-option v-for="item in WALLET_ACCOUNT_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input v-model="query.bizType" placeholder="业务类型" clearable style="width: 150px" @keyup.enter="search" />
        <el-date-picker
          v-model="timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 360px"
          @change="search"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table :data="rows" border stripe v-loading="loading">
        <el-table-column prop="storeName" label="门店" min-width="140" show-overflow-tooltip />
        <el-table-column label="账户" width="110">
          <template #default="{ row }">{{ WALLET_ACCOUNT[row.account] || '-' }}</template>
        </el-table-column>
        <el-table-column prop="bizType" label="业务类型" width="150" />
        <el-table-column label="变动" width="130">
          <template #default="{ row }">
            <span :class="row.direction === 1 ? 'money' : 'text-danger'">
              {{ row.direction === 1 ? '+' : '-' }}{{ fen2yuan(row.amount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="变动后余额" width="130">
          <template #default="{ row }">{{ fen2yuan(row.balanceAfter) }}</template>
        </el-table-column>
        <el-table-column prop="bizNo" label="业务单号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="170" />
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import StoreSelect from '../../components/StoreSelect.vue';
import { financeApi } from '../../api';
import { WALLET_ACCOUNT, WALLET_ACCOUNT_OPTIONS } from '../../utils/dict';
import { fen2yuan, toQuery } from '../../utils/format';

const loading = ref(false);
const rows = ref([]);
const total = ref(0);
const timeRange = ref([]);
const query = reactive({ storeId: null, account: null, bizType: '', page: 1, size: 20 });

async function load() {
  loading.value = true;
  try {
    const [startTime, endTime] = timeRange.value || [];
    const data = await financeApi.walletLogs(toQuery({ ...query, startTime, endTime }));
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
  query.account = null;
  query.bizType = '';
  timeRange.value = [];
  search();
}

onMounted(load);
</script>
