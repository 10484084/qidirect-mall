<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">推荐奖励</span>
      <el-button type="primary" @click="openConfig">提成比例配置</el-button>
    </div>

    <el-card shadow="never">
      <div class="filter-bar">
        <StoreSelect v-model="query.storeId" placeholder="提成归属门店" style="width: 220px" @change="search" />
        <StoreSelect v-model="query.fromStoreId" placeholder="来源门店" style="width: 220px" @change="search" />
        <el-select v-model="query.bizType" placeholder="业务类型" clearable style="width: 140px" @change="search">
          <el-option v-for="item in BIZ_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
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
        <el-table-column prop="bizType" label="业务类型" width="100">
          <template #default="{ row }">{{ BIZ_TYPE[row.bizType] || row.bizType }}</template>
        </el-table-column>
        <el-table-column prop="bizNo" label="业务单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="fromStoreName" label="来源门店" min-width="140" show-overflow-tooltip />
        <el-table-column prop="toStoreName" label="提成门店" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="openStore(row.toStoreId)">{{ row.toStoreName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="层级" width="80">
          <template #default="{ row }">{{ COMMISSION_LEVEL[row.level] || '-' }}</template>
        </el-table-column>
        <el-table-column label="比例" width="80">
          <template #default="{ row }">{{ ratio2percent(row.rate) }}%</template>
        </el-table-column>
        <el-table-column label="计算基数" width="120">
          <template #default="{ row }"><span class="money">{{ fen2yuan(row.baseAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="提成金额" width="120">
          <template #default="{ row }"><span class="money">{{ fen2yuan(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta(COMMISSION_STATUS, row.status).type">
              {{ statusMeta(COMMISSION_STATUS, row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="生成时间" width="170" />
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

    <el-dialog v-model="configVisible" title="提成比例配置" width="420px">
      <el-form :model="configForm" label-width="120px">
        <el-form-item label="一级提成比例">
          <el-input-number v-model="configForm.rate1Percent" :min="0" :max="100" :precision="2" :step="1" />
          <span class="text-muted">%</span>
        </el-form-item>
        <el-form-item label="二级提成比例">
          <el-input-number v-model="configForm.rate2Percent" :min="0" :max="100" :precision="2" :step="1" />
          <span class="text-muted">%</span>
        </el-form-item>
        <el-alert type="info" :closable="false" title="比例调整仅影响此后新产生的提成流水" />
      </el-form>
      <template #footer>
        <el-button @click="configVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="storeVisible" title="门店提成明细" size="64%">
      <el-descriptions v-if="storeDetail" :column="3" border>
        <el-descriptions-item label="门店">{{ storeDetail.storeName }}</el-descriptions-item>
        <el-descriptions-item label="档位">{{ storeDetail.levelName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="流水笔数">{{ storeDetail.recordCount }}</el-descriptions-item>
        <el-descriptions-item label="提成合计">
          <span class="money">{{ fen2yuan(storeDetail.totalAmount) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="一级合计">
          <span class="money">{{ fen2yuan(storeDetail.level1Amount) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="二级合计">
          <span class="money">{{ fen2yuan(storeDetail.level2Amount) }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-table :data="storeRecords" border stripe class="mt16" v-loading="storeLoading">
        <el-table-column prop="bizType" label="业务类型" width="100">
          <template #default="{ row }">{{ BIZ_TYPE[row.bizType] || row.bizType }}</template>
        </el-table-column>
        <el-table-column prop="bizNo" label="业务单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="fromStoreName" label="来源门店" min-width="140" show-overflow-tooltip />
        <el-table-column label="层级" width="80">
          <template #default="{ row }">{{ COMMISSION_LEVEL[row.level] || '-' }}</template>
        </el-table-column>
        <el-table-column label="提成金额" width="120">
          <template #default="{ row }"><span class="money">{{ fen2yuan(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta(COMMISSION_STATUS, row.status).type">
              {{ statusMeta(COMMISSION_STATUS, row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="生成时间" width="170" />
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="storeQuery.page"
          :page-size="storeQuery.size"
          :total="storeTotal"
          layout="total, prev, pager, next"
          @current-change="loadStoreRecords"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import StoreSelect from '../../components/StoreSelect.vue';
import { commissionApi } from '../../api';
import { BIZ_TYPE, BIZ_TYPE_OPTIONS, COMMISSION_LEVEL, COMMISSION_STATUS, statusMeta } from '../../utils/dict';
import { fen2yuan, percent2ratio, ratio2percent, toQuery } from '../../utils/format';

const loading = ref(false);
const rows = ref([]);
const total = ref(0);
const timeRange = ref([]);
const query = reactive({ storeId: null, fromStoreId: null, bizType: '', page: 1, size: 20 });

const configVisible = ref(false);
const saving = ref(false);
const configForm = reactive({ rate1Percent: 0, rate2Percent: 0 });

const storeVisible = ref(false);
const storeLoading = ref(false);
const storeDetail = ref(null);
const storeRecords = ref([]);
const storeTotal = ref(0);
const storeQuery = reactive({ storeId: null, page: 1, size: 20 });

async function load() {
  loading.value = true;
  try {
    const [startTime, endTime] = timeRange.value || [];
    const data = await commissionApi.records(
      toQuery({
        storeId: query.storeId,
        fromStoreId: query.fromStoreId,
        bizType: query.bizType,
        startTime,
        endTime,
        page: query.page,
        size: query.size
      })
    );
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
  query.fromStoreId = null;
  query.bizType = '';
  timeRange.value = [];
  search();
}

async function openConfig() {
  const data = await commissionApi.config();
  configForm.rate1Percent = Number(ratio2percent(data.rate1));
  configForm.rate2Percent = Number(ratio2percent(data.rate2));
  configVisible.value = true;
}

async function saveConfig() {
  saving.value = true;
  try {
    await commissionApi.updateConfig({
      rate1: percent2ratio(configForm.rate1Percent),
      rate2: percent2ratio(configForm.rate2Percent)
    });
    ElMessage.success('比例已更新');
    configVisible.value = false;
  } finally {
    saving.value = false;
  }
}

async function loadStoreRecords() {
  storeLoading.value = true;
  try {
    const data = await commissionApi.storeRecords(storeQuery.storeId, {
      page: storeQuery.page,
      size: storeQuery.size
    });
    storeRecords.value = data.records || [];
    storeTotal.value = data.total || 0;
  } finally {
    storeLoading.value = false;
  }
}

async function openStore(storeId) {
  storeQuery.storeId = storeId;
  storeQuery.page = 1;
  storeDetail.value = null;
  storeRecords.value = [];
  storeVisible.value = true;
  storeDetail.value = await commissionApi.storeDetail(storeId);
  await loadStoreRecords();
}

onMounted(load);
</script>
