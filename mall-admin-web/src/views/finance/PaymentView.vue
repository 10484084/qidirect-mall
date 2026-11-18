<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">支付流水</span>
    </div>

    <el-card shadow="never">
      <div class="filter-bar">
        <el-input v-model="query.payNo" placeholder="支付单号" clearable style="width: 200px" @keyup.enter="search" />
        <StoreSelect v-model="query.storeId" placeholder="门店" style="width: 220px" @change="search" />
        <el-select v-model="query.bizType" placeholder="业务类型" clearable style="width: 140px" @change="search">
          <el-option v-for="item in BIZ_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.status" placeholder="支付状态" clearable style="width: 130px" @change="search">
          <el-option v-for="item in PAY_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.channel" placeholder="渠道" clearable style="width: 130px" @change="search">
          <el-option v-for="item in PAY_CHANNEL_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
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
        <el-table-column prop="payNo" label="支付单号" min-width="190" show-overflow-tooltip />
        <el-table-column label="业务" width="110">
          <template #default="{ row }">{{ BIZ_TYPE[row.bizType] || row.bizType }}</template>
        </el-table-column>
        <el-table-column prop="storeName" label="门店" min-width="130" show-overflow-tooltip />
        <el-table-column label="金额" width="120">
          <template #default="{ row }"><span class="money">{{ fen2yuan(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="渠道" width="110">
          <template #default="{ row }">
            {{ PAY_CHANNEL[row.channel] || '-' }}
            <span v-if="row.offlineMethod" class="text-muted">/{{ OFFLINE_METHOD[row.offlineMethod] }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta(PAY_STATUS, row.status).type">
              {{ statusMeta(PAY_STATUS, row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="outTradeNo" label="第三方单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="退款" width="130">
          <template #default="{ row }">
            <span v-if="row.refundAmount" class="text-danger">{{ fen2yuan(row.refundAmount) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="paidAt" label="支付时间" width="170" />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openNotifies(row)">回调报文</el-button>
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

    <el-drawer v-model="notifyVisible" :title="`回调报文 ${currentPayNo}`" size="56%">
      <el-empty v-if="!notifies.length" description="暂无回调记录" />
      <el-card v-for="item in notifies" :key="item.id" shadow="never" class="mt8">
        <div class="text-muted">
          {{ item.createdAt }} · 验签{{ item.verified === 1 ? '通过' : '失败' }} · 处理{{ item.handled === 1 ? '完成' : '未处理' }}
        </div>
        <pre class="pre-wrap">{{ item.rawBody }}</pre>
      </el-card>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import StoreSelect from '../../components/StoreSelect.vue';
import { financeApi } from '../../api';
import {
  BIZ_TYPE,
  BIZ_TYPE_OPTIONS,
  OFFLINE_METHOD,
  PAY_CHANNEL,
  PAY_CHANNEL_OPTIONS,
  PAY_STATUS,
  PAY_STATUS_OPTIONS,
  statusMeta
} from '../../utils/dict';
import { fen2yuan, toQuery } from '../../utils/format';

const loading = ref(false);
const rows = ref([]);
const total = ref(0);
const timeRange = ref([]);
const query = reactive({
  payNo: '',
  storeId: null,
  bizType: '',
  status: null,
  channel: null,
  page: 1,
  size: 20
});

const notifyVisible = ref(false);
const notifies = ref([]);
const currentPayNo = ref('');

async function load() {
  loading.value = true;
  try {
    const [startTime, endTime] = timeRange.value || [];
    const data = await financeApi.payments(toQuery({ ...query, startTime, endTime }));
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
  query.payNo = '';
  query.storeId = null;
  query.bizType = '';
  query.status = null;
  query.channel = null;
  timeRange.value = [];
  search();
}

async function openNotifies(row) {
  currentPayNo.value = row.payNo;
  notifies.value = (await financeApi.notifies(row.payNo)) || [];
  notifyVisible.value = true;
}

onMounted(load);
</script>
