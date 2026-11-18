<template>
  <div class="page performance-page">
    <PageHeader title="团队业绩" />

    <div class="page-body">
      <van-field
        :model-value="month"
        readonly
        label="结算月份"
        is-link
        @click="pickerVisible = true"
      />

      <van-loading v-if="loading" class="loading" />
      <template v-else-if="data">
        <div class="hero">
          <div class="muted small">团队业绩（自己 + 子树）</div>
          <div class="hero-value">{{ fenToYuan(data.teamPerformance) }}</div>
          <div class="hero-sub">
            <span>门店自身 {{ fenToYuan(data.ownPerformance) }}</span>
            <span>当前档位 {{ data.levelName || '-' }}</span>
          </div>
        </div>

        <div v-if="data.bonusEnabled === false" class="card">
          <van-notice-bar left-icon="info-o" text="当前档位不享受业绩奖，仅最高档位门店参与结算。" />
        </div>

        <div class="card">
          <div class="card-title">阶梯进度</div>
          <div class="progress-line">
            <span class="muted small">当前命中：{{ data.currentMinAmount ? `业绩满 ¥${fenToYuan(data.currentMinAmount)}` : '未命中档位' }}</span>
            <span class="muted small">比例 {{ ratePercent }}%</span>
          </div>
          <van-progress
            :percentage="progressPercent"
            :show-pivot="false"
            stroke-width="8"
            color="#1f6feb"
            class="mt-8"
          />
          <div class="muted small mt-8">
            {{ data.nextMinAmount ? `距离下一档还差 ¥${fenToYuan(data.gapToNext)}` : '已达最高档位' }}
          </div>
          <div class="flex-between mt-12">
            <span class="muted">预计业绩奖</span>
            <span class="money">{{ fenToYuan(data.expectedBonus) }}</span>
          </div>
        </div>

        <div class="card">
          <div class="card-title">奖励阶梯</div>
          <div v-for="tier in data.tiers || []" :key="tier.ruleId" class="tier" :class="{ reached: tier.reached }">
            <span>业绩满 ¥{{ fenToYuan(tier.minAmount) }}</span>
            <span>奖励比例 {{ (Number(tier.rate) * 100).toFixed(2) }}%</span>
            <van-tag v-if="tier.reached" type="success" plain>已达成</van-tag>
          </div>
          <div v-if="!(data.tiers || []).length" class="text-empty">暂无阶梯规则</div>
        </div>

        <div class="card">
          <div class="card-title">业绩调整明细</div>
          <div v-for="record in data.adjustments || []" :key="record.id" class="log-line">
            <div>
              <div>{{ record.bizType }} · {{ record.bizNo || '-' }}</div>
              <div class="muted small">{{ formatTime(record.createdAt) }} {{ record.remark || '' }}</div>
            </div>
            <span :class="record.amount >= 0 ? 'success' : 'danger'">
              {{ record.amount >= 0 ? '+' : '-' }}{{ fenToYuan(Math.abs(record.amount)) }}
            </span>
          </div>
          <div v-if="!(data.adjustments || []).length" class="text-empty">暂无调整</div>
        </div>

        <div class="card">
          <div class="card-title">奖励记录</div>
          <div v-for="bonus in data.bonuses || []" :key="bonus.id" class="log-line">
            <div>
              <div>{{ bonus.month }} 业绩奖</div>
              <div class="muted small">
                业绩 ¥{{ fenToYuan(bonus.performance) }} · 比例 {{ (Number(bonus.rate || 0) * 100).toFixed(2) }}%
                <span v-if="bonus.status === 2"> · 已冲正</span>
              </div>
            </div>
            <span class="money">{{ fenToYuan(bonus.bonusAmount) }}</span>
          </div>
          <div v-if="!(data.bonuses || []).length" class="text-empty">暂无奖励记录</div>
        </div>
      </template>
      <div v-else class="text-empty">暂无业绩数据</div>
    </div>

    <van-popup v-model:show="pickerVisible" position="bottom" round>
      <van-picker
        :columns="monthColumns"
        :model-value="[month]"
        @confirm="onPick"
        @cancel="pickerVisible = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import PageHeader from '../components/PageHeader.vue';
import { performanceApi } from '../api';
import { fenToYuan, formatTime, recentMonths, todayMonth } from '../utils/format';

const month = ref(todayMonth());
const data = ref(null);
const loading = ref(true);
const pickerVisible = ref(false);
const monthColumns = recentMonths(12);

const ratePercent = computed(() => (data.value && data.value.currentRate ? (Number(data.value.currentRate) * 100).toFixed(2) : '0.00'));

const progressPercent = computed(() => {
  if (!data.value) return 0;
  const target = data.value.nextMinAmount || data.value.currentMinAmount || 0;
  if (!target) return 100;
  return Math.min(100, Math.round((data.value.teamPerformance / target) * 100));
});

async function load() {
  loading.value = true;
  try {
    data.value = await performanceApi.get(month.value);
  } catch (e) {
    data.value = null;
  } finally {
    loading.value = false;
  }
}

function onPick({ selectedValues }) {
  month.value = selectedValues[0];
  pickerVisible.value = false;
  load();
}

onMounted(load);
</script>

<style scoped>
.loading {
  display: block;
  text-align: center;
  padding: 40px 0;
}

.hero {
  margin: 12px 0;
  padding: 18px;
  border-radius: 14px;
  background: linear-gradient(150deg, #2b7cf5, #0f4fbf);
  color: #fff;
}

.hero-value {
  font-size: 28px;
  font-weight: 600;
  margin: 6px 0;
}

.hero-sub {
  display: flex;
  gap: 14px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.78);
}

.progress-line {
  display: flex;
  justify-content: space-between;
}

.tier {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  padding: 8px 0;
  border-bottom: 1px solid #f4f5f7;
  color: var(--text-sub);
}

.tier.reached {
  color: var(--text-main);
  font-weight: 500;
}

.log-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 13px;
  padding: 8px 0;
  border-bottom: 1px solid #f4f5f7;
}

.log-line:last-child {
  border-bottom: none;
}
</style>
