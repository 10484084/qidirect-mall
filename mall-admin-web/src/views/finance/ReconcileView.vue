<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">资金对账</span>
      <el-button type="primary" :loading="loading" @click="check">重新对账</el-button>
    </div>

    <el-card shadow="never" v-loading="loading">
      <template v-if="result">
        <el-alert
          :type="result.balanced ? 'success' : 'error'"
          :title="result.balanced ? '对账通过：钱包余额与流水完全一致' : '对账存在差异，请核查下方明细'"
          :closable="false"
          show-icon
        />
        <div class="text-muted mt8">检查时间：{{ result.checkedAt }}</div>

        <el-row :gutter="16" class="mt16">
          <el-col :span="8">
            <el-card shadow="never">
              <div class="text-muted">预存余额差异</div>
              <div :class="result.depositDiff === 0 ? 'money' : 'text-danger'">{{ fen2yuan(result.depositDiff) }}</div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never">
              <div class="text-muted">佣金余额差异</div>
              <div :class="result.commissionDiff === 0 ? 'money' : 'text-danger'">
                {{ fen2yuan(result.commissionDiff) }}
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never">
              <div class="text-muted">冻结金额差异</div>
              <div :class="result.frozenDiff === 0 ? 'money' : 'text-danger'">{{ fen2yuan(result.frozenDiff) }}</div>
            </el-card>
          </el-col>
        </el-row>

        <el-tabs v-model="tab" class="mt16">
          <el-tab-pane label="预存差异" name="deposit">
            <el-table :data="result.depositMismatches || []" border stripe>
              <el-table-column prop="storeId" label="门店ID" width="120" />
              <el-table-column label="钱包余额" width="150">
                <template #default="{ row }">{{ fen2yuan(row.walletValue) }}</template>
              </el-table-column>
              <el-table-column label="流水合计" width="150">
                <template #default="{ row }">{{ fen2yuan(row.flowValue) }}</template>
              </el-table-column>
              <el-table-column label="差额">
                <template #default="{ row }"><span class="text-danger">{{ fen2yuan(row.diff) }}</span></template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="佣金差异" name="commission">
            <el-table :data="result.commissionMismatches || []" border stripe>
              <el-table-column prop="storeId" label="门店ID" width="120" />
              <el-table-column label="钱包余额" width="150">
                <template #default="{ row }">{{ fen2yuan(row.walletValue) }}</template>
              </el-table-column>
              <el-table-column label="流水合计" width="150">
                <template #default="{ row }">{{ fen2yuan(row.flowValue) }}</template>
              </el-table-column>
              <el-table-column label="差额">
                <template #default="{ row }"><span class="text-danger">{{ fen2yuan(row.diff) }}</span></template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="冻结差异" name="frozen">
            <el-table :data="result.frozenMismatches || []" border stripe>
              <el-table-column prop="storeId" label="门店ID" width="120" />
              <el-table-column label="钱包余额" width="150">
                <template #default="{ row }">{{ fen2yuan(row.walletValue) }}</template>
              </el-table-column>
              <el-table-column label="流水合计" width="150">
                <template #default="{ row }">{{ fen2yuan(row.flowValue) }}</template>
              </el-table-column>
              <el-table-column label="差额">
                <template #default="{ row }"><span class="text-danger">{{ fen2yuan(row.diff) }}</span></template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
      <el-empty v-else description="点击「重新对账」开始检查" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { financeApi } from '../../api';
import { fen2yuan } from '../../utils/format';

const loading = ref(false);
const result = ref(null);
const tab = ref('deposit');

async function check() {
  loading.value = true;
  try {
    result.value = await financeApi.reconcile();
  } finally {
    loading.value = false;
  }
}

onMounted(check);
</script>
