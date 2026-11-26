<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">团队奖励</span>
    </div>

    <el-card shadow="never">
      <el-tabs v-model="tab">
        <el-tab-pane label="业绩规则" name="rules">
          <div class="filter-bar">
            <el-button type="primary" @click="openRule()">新增规则</el-button>
            <span class="text-muted">按业绩金额从低到高匹配首档满足的规则</span>
          </div>
          <el-table :data="rules" border stripe v-loading="ruleLoading">
            <el-table-column label="业绩门槛" width="200">
              <template #default="{ row }">
                业绩 ≥ <span class="money">{{ fen2yuan(row.minAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="奖励比例" width="140">
              <template #default="{ row }">{{ ratio2percent(row.rate) }}%</template>
            </el-table-column>
            <el-table-column prop="sort" label="排序" width="100" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRule(row)">编辑</el-button>
                <el-button link type="danger" @click="removeRule(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="业绩流水" name="records">
          <div class="filter-bar">
            <StoreSelect v-model="recordQuery.storeId" placeholder="门店" style="width: 220px" @change="searchRecords" />
            <el-date-picker
              v-model="recordQuery.month"
              type="month"
              placeholder="统计月份"
              value-format="YYYY-MM"
              style="width: 160px"
              @change="searchRecords"
            />
            <el-select v-model="recordQuery.bizType" placeholder="业务类型" clearable style="width: 140px" @change="searchRecords">
              <el-option v-for="item in BIZ_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-button type="primary" @click="searchRecords">查询</el-button>
            <el-button @click="openAdjust">业绩修正</el-button>
          </div>
          <el-table :data="records" border stripe v-loading="recordLoading">
            <el-table-column prop="month" label="月份" width="100" />
            <el-table-column prop="storeName" label="门店" min-width="140" show-overflow-tooltip />
            <el-table-column prop="bizType" label="类型" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.bizType === 'ADJUST'" type="warning">修正</el-tag>
                <span v-else>{{ BIZ_TYPE[row.bizType] || row.bizType }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="bizNo" label="业务单号" min-width="170" show-overflow-tooltip />
            <el-table-column label="业绩金额" width="130">
              <template #default="{ row }"><span class="money">{{ fen2yuan(row.amount) }}</span></template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="产生时间" width="170" />
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="recordQuery.page"
              v-model:page-size="recordQuery.size"
              :total="recordTotal"
              :page-sizes="[20, 50, 100]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadRecords"
              @current-change="loadRecords"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="月结奖金" name="bonuses">
          <div class="filter-bar">
            <StoreSelect v-model="bonusQuery.storeId" placeholder="门店" style="width: 220px" @change="searchBonuses" />
            <el-date-picker
              v-model="bonusQuery.month"
              type="month"
              placeholder="结算月份"
              value-format="YYYY-MM"
              style="width: 160px"
              @change="searchBonuses"
            />
            <el-button type="primary" @click="searchBonuses">查询</el-button>
            <el-button type="success" :loading="settling" @click="settle">执行月结</el-button>
          </div>
          <el-table :data="bonuses" border stripe v-loading="bonusLoading">
            <el-table-column prop="month" label="月份" width="100" />
            <el-table-column prop="storeName" label="门店" min-width="140" show-overflow-tooltip />
            <el-table-column label="业绩" width="130">
              <template #default="{ row }"><span class="money">{{ fen2yuan(row.performance) }}</span></template>
            </el-table-column>
            <el-table-column label="适用比例" width="100">
              <template #default="{ row }">{{ ratio2percent(row.rate) }}%</template>
            </el-table-column>
            <el-table-column label="奖金金额" width="130">
              <template #default="{ row }"><span class="money">{{ fen2yuan(row.bonusAmount) }}</span></template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusMeta(BONUS_STATUS, row.status).type">
                  {{ statusMeta(BONUS_STATUS, row.status).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="settledAt" label="结算时间" width="170" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 1 && !row.revisionOf" link type="danger" @click="openReverse(row)">
                  冲正
                </el-button>
                <span v-else-if="row.revisionOf" class="text-muted">冲正记录</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="bonusQuery.page"
              v-model:page-size="bonusQuery.size"
              :total="bonusTotal"
              :page-sizes="[20, 50, 100]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadBonuses"
              @current-change="loadBonuses"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="ruleVisible" :title="ruleForm.id ? '编辑业绩规则' : '新增业绩规则'" width="460px">
      <el-form :model="ruleForm" label-width="110px">
        <el-form-item label="业绩门槛">
          <el-input-number v-model="ruleForm.minYuan" :min="0" :precision="2" :step="100" />
          <span class="text-muted">元</span>
        </el-form-item>
        <el-form-item label="奖励比例">
          <el-input-number v-model="ruleForm.ratePercent" :min="0" :max="100" :precision="2" />
          <span class="text-muted">%</span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="ruleForm.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="ruleForm.status" :active-value="1" :inactive-value="0" active-text="启用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="adjustVisible" title="业绩修正（结算前）" width="460px">
      <el-form :model="adjustForm" label-width="100px">
        <el-form-item label="门店" required>
          <StoreSelect v-model="adjustForm.storeId" placeholder="选择门店" />
        </el-form-item>
        <el-form-item label="月份" required>
          <el-date-picker v-model="adjustForm.month" type="month" value-format="YYYY-MM" placeholder="选择月份" />
        </el-form-item>
        <el-form-item label="修正金额" required>
          <el-input-number v-model="adjustForm.amountYuan" :precision="2" :step="100" />
          <span class="text-muted">元，可为负数</span>
        </el-form-item>
        <el-form-item label="原因" required>
          <el-input v-model="adjustForm.remark" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
        <el-alert type="info" :closable="false" title="仅新增修正流水，已结算月份请使用冲正" />
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAdjust">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reverseVisible" title="奖金冲正" width="460px">
      <el-descriptions v-if="reverseRow" :column="2" border>
        <el-descriptions-item label="门店">{{ reverseRow.storeName }}</el-descriptions-item>
        <el-descriptions-item label="月份">{{ reverseRow.month }}</el-descriptions-item>
        <el-descriptions-item label="原业绩">{{ fen2yuan(reverseRow.performance) }}</el-descriptions-item>
        <el-descriptions-item label="原奖金">{{ fen2yuan(reverseRow.bonusAmount) }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="reverseForm" label-width="110px" class="mt16">
        <el-form-item label="修正后业绩" required>
          <el-input-number v-model="reverseForm.correctedYuan" :min="0" :precision="2" :step="100" />
          <span class="text-muted">元</span>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="reverseForm.remark" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
        <el-alert type="warning" :closable="false" title="将新增冲正记录，钱包仅记账差额" />
      </el-form>
      <template #footer>
        <el-button @click="reverseVisible = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="saveReverse">确认冲正</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import StoreSelect from '../../components/StoreSelect.vue';
import { performanceApi } from '../../api';
import { BIZ_TYPE, BIZ_TYPE_OPTIONS, BONUS_STATUS, statusMeta } from '../../utils/dict';
import { fen2yuan, percent2ratio, ratio2percent, toQuery, yuan2fen } from '../../utils/format';

const tab = ref('rules');
const saving = ref(false);
const settling = ref(false);

const rules = ref([]);
const ruleLoading = ref(false);
const ruleVisible = ref(false);
const ruleForm = reactive({ id: null, minYuan: 0, ratePercent: 0, sort: 0, status: 1 });

const records = ref([]);
const recordTotal = ref(0);
const recordLoading = ref(false);
const recordQuery = reactive({ storeId: null, month: '', bizType: '', page: 1, size: 20 });

const bonuses = ref([]);
const bonusTotal = ref(0);
const bonusLoading = ref(false);
const bonusQuery = reactive({ storeId: null, month: '', page: 1, size: 20 });

const adjustVisible = ref(false);
const adjustForm = reactive({ storeId: null, month: '', amountYuan: 0, remark: '' });

const reverseVisible = ref(false);
const reverseRow = ref(null);
const reverseForm = reactive({ correctedYuan: 0, remark: '' });

async function loadRules() {
  ruleLoading.value = true;
  try {
    rules.value = (await performanceApi.rules()) || [];
  } finally {
    ruleLoading.value = false;
  }
}

function openRule(row) {
  ruleForm.id = row ? row.id : null;
  ruleForm.minYuan = row ? Number(fen2yuan(row.minAmount)) : 0;
  ruleForm.ratePercent = row ? Number(ratio2percent(row.rate)) : 0;
  ruleForm.sort = row ? row.sort || 0 : 0;
  ruleForm.status = row ? row.status ?? 1 : 1;
  ruleVisible.value = true;
}

async function saveRule() {
  saving.value = true;
  const payload = {
    minAmount: yuan2fen(ruleForm.minYuan),
    rate: percent2ratio(ruleForm.ratePercent),
    sort: ruleForm.sort,
    status: ruleForm.status
  };
  try {
    if (ruleForm.id) await performanceApi.updateRule(ruleForm.id, payload);
    else await performanceApi.createRule(payload);
    ElMessage.success('已保存');
    ruleVisible.value = false;
    loadRules();
  } finally {
    saving.value = false;
  }
}

async function removeRule(row) {
  await ElMessageBox.confirm('确认删除该业绩规则？', '提示', { type: 'warning' });
  await performanceApi.deleteRule(row.id);
  ElMessage.success('已删除');
  loadRules();
}

async function loadRecords() {
  recordLoading.value = true;
  try {
    const data = await performanceApi.records(toQuery(recordQuery));
    records.value = data.records || [];
    recordTotal.value = data.total || 0;
  } finally {
    recordLoading.value = false;
  }
}

function searchRecords() {
  recordQuery.page = 1;
  loadRecords();
}

async function loadBonuses() {
  bonusLoading.value = true;
  try {
    const data = await performanceApi.bonuses(toQuery(bonusQuery));
    bonuses.value = data.records || [];
    bonusTotal.value = data.total || 0;
  } finally {
    bonusLoading.value = false;
  }
}

function searchBonuses() {
  bonusQuery.page = 1;
  loadBonuses();
}

function openAdjust() {
  adjustForm.storeId = null;
  adjustForm.month = recordQuery.month || '';
  adjustForm.amountYuan = 0;
  adjustForm.remark = '';
  adjustVisible.value = true;
}

async function saveAdjust() {
  if (!adjustForm.storeId) return ElMessage.warning('请选择门店');
  if (!adjustForm.month) return ElMessage.warning('请选择月份');
  if (!adjustForm.remark) return ElMessage.warning('请填写原因');
  saving.value = true;
  try {
    await performanceApi.adjust({
      storeId: adjustForm.storeId,
      month: adjustForm.month,
      amount: yuan2fen(adjustForm.amountYuan),
      remark: adjustForm.remark
    });
    ElMessage.success('已提交修正');
    adjustVisible.value = false;
    loadRecords();
  } finally {
    saving.value = false;
  }
}

function openReverse(row) {
  reverseRow.value = row;
  reverseForm.correctedYuan = Number(fen2yuan(row.performance));
  reverseForm.remark = '';
  reverseVisible.value = true;
}

async function saveReverse() {
  saving.value = true;
  try {
    await performanceApi.reverse(reverseRow.value.id, {
      correctedPerformance: yuan2fen(reverseForm.correctedYuan),
      remark: reverseForm.remark
    });
    ElMessage.success('已冲正');
    reverseVisible.value = false;
    loadBonuses();
  } finally {
    saving.value = false;
  }
}

async function settle() {
  const month = bonusQuery.month;
  if (!month) return ElMessage.warning('请先在筛选条件选择结算月份');
  await ElMessageBox.confirm(`确认执行 ${month} 月结？重复执行将跳过已结算门店。`, '月结', { type: 'warning' });
  settling.value = true;
  try {
    const result = await performanceApi.settle(month);
    const failureText = result.failures && result.failures.length ? `，失败：${result.failures.join('；')}` : '';
    ElMessageBox.alert(
      `候选 ${result.candidateCount} 家，成功 ${result.settledCount} 家，跳过 ${result.skippedCount} 家，失败 ${result.failedCount} 家${failureText}`,
      '月结结果',
      { type: 'success' }
    );
    loadBonuses();
  } finally {
    settling.value = false;
  }
}

onMounted(() => {
  loadRules();
  loadRecords();
  loadBonuses();
});
</script>
