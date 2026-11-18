<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">操作日志</span>
    </div>

    <el-card shadow="never">
      <div class="filter-bar">
        <el-input v-model="query.module" placeholder="模块" clearable style="width: 140px" @keyup.enter="search" />
        <el-input v-model="query.action" placeholder="动作" clearable style="width: 160px" @keyup.enter="search" />
        <el-input v-model="query.operator" placeholder="操作人" clearable style="width: 160px" @keyup.enter="search" />
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
        <el-table-column prop="operator" label="操作人" width="140" />
        <el-table-column prop="module" label="模块" width="130" />
        <el-table-column prop="action" label="动作" width="190" />
        <el-table-column prop="target" label="对象" min-width="150" show-overflow-tooltip />
        <el-table-column label="详情" min-width="260">
          <template #default="{ row }">
            <el-link type="primary" @click="openDetail(row)">查看</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="140" />
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

    <el-dialog v-model="detailVisible" title="日志详情" width="640px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="操作人">{{ current.operator }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ current.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ current.module }}</el-descriptions-item>
        <el-descriptions-item label="动作">{{ current.action }}</el-descriptions-item>
        <el-descriptions-item label="对象" :span="2">{{ current.target }}</el-descriptions-item>
        <el-descriptions-item label="IP" :span="2">{{ current.ip }}</el-descriptions-item>
      </el-descriptions>
      <pre class="pre-wrap mt16">{{ prettyDetail }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { operationLogApi } from '../../api';
import { toQuery } from '../../utils/format';

const loading = ref(false);
const rows = ref([]);
const total = ref(0);
const timeRange = ref([]);
const query = reactive({ module: '', action: '', operator: '', page: 1, size: 20 });

const detailVisible = ref(false);
const current = ref(null);

const prettyDetail = computed(() => {
  const raw = current.value?.detail || '';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch (e) {
    return raw || '无详情';
  }
});

async function load() {
  loading.value = true;
  try {
    const [startTime, endTime] = timeRange.value || [];
    const data = await operationLogApi.page(toQuery({ ...query, startTime, endTime }));
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
  query.module = '';
  query.action = '';
  query.operator = '';
  timeRange.value = [];
  search();
}

function openDetail(row) {
  current.value = row;
  detailVisible.value = true;
}

onMounted(load);
</script>
