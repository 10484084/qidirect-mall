<template>
  <div class="page withdraw-page">
    <PageHeader title="提现" />

    <div class="page-body">
      <div class="card">
        <div class="card-title">
          提现额度
          <van-tag :type="config.open ? 'success' : 'warning'">{{ config.open ? '今日开放' : '今日不开放' }}</van-tag>
        </div>
        <div class="kv"><span class="muted">可提佣金余额</span><span class="money">{{ fenToYuan(config.availableAmount) }}</span></div>
        <div class="kv"><span class="muted">可提现范围</span><span class="small-tip-text">仅佣金可提现，预存余额只能下单抵扣</span></div>
        <div class="kv"><span class="muted">最低提现金额</span><span>¥{{ fenToYuan(config.minAmount) }}</span></div>
        <div v-if="!config.open && config.nextOpenDate" class="kv">
          <span class="muted">下次开放</span><span>{{ config.nextOpenDate }}</span>
        </div>
      </div>

      <div class="card">
        <div class="card-title">收款卡</div>
        <template v-if="config.bankBound">
          <div class="kv"><span class="muted">持卡人</span><span>{{ config.bankHolder }}</span></div>
          <div class="kv"><span class="muted">开户行</span><span>{{ config.bankName }}</span></div>
          <div class="kv"><span class="muted">卡号</span><span>{{ config.bankAccountMasked }}</span></div>
        </template>
        <div v-else class="bank-missing">
          暂未绑定收款卡
          <van-button size="small" type="primary" round @click="$router.push('/profile')">去绑定</van-button>
        </div>
      </div>

      <div class="card">
        <div class="card-title">提现金额</div>
        <van-field v-model="amount" type="number" label="金额（元）" placeholder="请输入提现金额">
          <template #button>
            <span class="muted small" @click="amount = fenToYuan(config.availableAmount)">全部</span>
          </template>
        </van-field>
        <div class="form-btn">
          <van-button type="primary" block round :loading="submitting" @click="submit">提交提现申请</van-button>
        </div>
        <p class="muted small">申请后需厂家审核，审核通过后线下打款；驳回或打款失败不会产生余额流水。</p>
      </div>

      <div class="card">
        <div class="card-title">提现记录</div>
        <div v-for="item in records" :key="item.id" class="record">
          <div class="flex-between">
            <span class="money">{{ fenToYuan(item.amount) }}</span>
            <van-tag :type="withdrawStatusType(item.status)">{{ withdrawStatusText(item.status) }}</van-tag>
          </div>
          <div class="muted small mt-8">申请时间 {{ formatTime(item.applyTime) }}</div>
          <div v-if="item.rejectReason" class="danger small mt-8">驳回原因：{{ item.rejectReason }}</div>
          <div v-if="item.failReason" class="danger small mt-8">失败原因：{{ item.failReason }}</div>
          <div v-if="item.batchNo" class="muted small mt-8">批次号：{{ item.batchNo }}</div>
        </div>
        <div v-if="!records.length" class="text-empty">暂无提现记录</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { showDialog, showToast } from 'vant';
import PageHeader from '../components/PageHeader.vue';
import { withdrawApi } from '../api';
import { fenToYuan, formatTime, withdrawStatusText, withdrawStatusType } from '../utils/format';

const config = ref({
  open: false,
  minAmount: 0,
  availableAmount: 0,
  openDays: '',
  nextOpenDate: '',
  bankBound: false
});
const records = ref([]);
const amount = ref('');
const submitting = ref(false);

async function load() {
  try {
    const [configData, pageData] = await Promise.all([
      withdrawApi.config(),
      withdrawApi.page({ page: 1, size: 20 })
    ]);
    config.value = configData;
    records.value = pageData.records || [];
  } catch (e) {
    // 错误已提示
  }
}

async function submit() {
  if (!config.value.bankBound) {
    showToast('请先绑定收款卡');
    return;
  }
  const fen = Math.round(Number(amount.value) * 100);
  if (!fen || fen <= 0) {
    showToast('请输入提现金额');
    return;
  }
  if (fen > config.value.availableAmount) {
    showToast('提现金额超过可提佣金余额');
    return;
  }
  if (fen < config.value.minAmount) {
    showToast(`提现金额不能低于 ¥${fenToYuan(config.value.minAmount)}`);
    return;
  }
  submitting.value = true;
  try {
    await withdrawApi.apply(fen);
    amount.value = '';
    await showDialog({ title: '提交成功', message: '提现申请已提交，请等待厂家审核。' });
    load();
  } finally {
    submitting.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.kv {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 13px;
  padding: 5px 0;
}

.form-btn {
  padding: 16px 4px 4px;
}

.small-tip-text {
  font-size: 12px;
  color: #969799;
  text-align: right;
  flex: 1;
}

.bank-missing {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-sub);
  font-size: 13px;
}

.record {
  padding: 10px 0;
  border-bottom: 1px solid #f4f5f7;
}

.record:last-child {
  border-bottom: none;
}
</style>
