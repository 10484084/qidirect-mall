<template>
  <div class="page purchase-page">
    <PageHeader title="加盟 / 预存" />

    <div class="page-body">
      <div v-if="profile" class="card current-card">
        <div class="flex-between">
          <div>
            <div class="muted small">当前档位</div>
            <div class="current-level">{{ user.joined ? profile.levelName : '未加盟' }}</div>
          </div>
          <div class="right-info">
            <div v-if="profile.upgradeDeadline" class="muted small">
              升级窗口至 {{ formatTime(profile.upgradeDeadline) }}
            </div>
            <div v-else class="muted small">加盟后可参与预存与升级</div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-title">选择购买入口</div>
        <div class="source-list">
          <div class="source-item" :class="{ active: sourceKey === 'resident' }" @click="selectSource('resident')">
            <div class="source-title">常驻预存 / 升级</div>
            <div class="muted small">随时可参与</div>
          </div>
          <div
            v-for="activity in activities"
            :key="activity.id"
            class="source-item"
            :class="{ active: sourceKey === String(activity.id) }"
            @click="selectSource(String(activity.id))"
          >
            <div class="source-title">{{ activity.title }}</div>
            <div class="muted small">限 {{ activity.limitPerStore }} 次/店</div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-title">选择档位</div>
        <div
          v-for="option in currentOptions"
          :key="option.levelId"
          class="option-item"
          :class="{ active: selectedLevelId === option.levelId }"
          @click="selectedLevelId = option.levelId"
        >
          <div class="flex-between">
            <div class="option-name">{{ option.levelName }}</div>
            <van-tag :type="option.action === 'JOIN' ? 'primary' : option.action === 'UPGRADE' ? 'warning' : 'default'">
              {{ actionText(option.action) }}
            </van-tag>
          </div>
          <div class="muted small mt-8">{{ option.description }}</div>
          <div class="flex-between mt-8">
            <span class="muted small">档位费 ¥{{ fenToYuan(option.fee) }}</span>
            <span>
              <span class="muted small">应付</span>
              <span class="money">{{ fenToYuan(option.payAmount) }}</span>
            </span>
          </div>
        </div>
        <div v-if="!currentOptions.length" class="text-empty">暂无可选档位</div>
      </div>

      <div class="card agreement-card">
        <van-checkbox v-model="agreed" icon-size="16">
          我已阅读并同意
          <a @click.stop="showAgreement">《{{ agreement.title || '加盟协议' }}》</a>
        </van-checkbox>
        <div v-if="agreement.version" class="muted small mt-8">协议版本：{{ agreement.version }}</div>
      </div>

      <van-notice-bar v-if="!user.authed" left-icon="info-o" text="未实名认证，请先完成实名后再购买" />
    </div>

    <div class="footer-bar">
      <div class="grow">
        <div class="muted small">应付金额</div>
        <div class="money pay-amount">{{ fenToYuan(currentOption ? currentOption.payAmount : 0) }}</div>
      </div>
      <van-button type="primary" round :loading="submitting" :disabled="!currentOption" @click="submit">
        {{ currentOption && currentOption.action === 'JOIN' ? '立即加盟' : '立即支付' }}
      </van-button>
    </div>

    <van-popup v-model:show="agreementVisible" position="bottom" round :style="{ height: '72%' }">
      <div class="agreement-popup">
        <div class="popup-title">{{ agreement.title }}</div>
        <div class="popup-content">{{ agreement.content }}</div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showDialog, showToast } from 'vant';
import PageHeader from '../components/PageHeader.vue';
import { purchaseApi, rechargeApi } from '../api';
import { useUserStore } from '../stores/user';
import { actionText, fenToYuan, formatTime } from '../utils/format';
import { assertPayAllowed } from '../utils/pay';

const router = useRouter();
const user = useUserStore();

const options = ref([]);
const activities = ref([]);
const agreement = ref({});
const agreementVisible = ref(false);
const sourceKey = ref('resident');
const selectedLevelId = ref(null);
const agreed = ref(false);
const submitting = ref(false);

const profile = computed(() => user.profile);

const currentOptions = computed(() => {
  if (sourceKey.value === 'resident') return options.value;
  const activity = activities.value.find((item) => String(item.id) === sourceKey.value);
  return activity ? activity.options || [] : [];
});

const currentOption = computed(() => currentOptions.value.find((item) => item.levelId === selectedLevelId.value) || null);

const currentActivityId = computed(() => (sourceKey.value === 'resident' ? null : Number(sourceKey.value)));

onMounted(async () => {
  try {
    if (!user.profile) await user.loadProfile();
    const [optionData, activityData, agreementData] = await Promise.all([
      purchaseApi.options(),
      rechargeApi.list().catch(() => []),
      purchaseApi.agreement().catch(() => ({}))
    ]);
    options.value = optionData || [];
    activities.value = activityData || [];
    agreement.value = agreementData || {};
    const first = options.value[0];
    if (first) selectedLevelId.value = first.levelId;
  } catch (e) {
    // 错误已提示
  }
});

function selectSource(key) {
  sourceKey.value = key;
  const first = currentOptions.value[0];
  selectedLevelId.value = first ? first.levelId : null;
}

function showAgreement() {
  if (!agreement.value.content) {
    showToast('协议内容加载失败，请稍后重试');
    return;
  }
  agreementVisible.value = true;
}

async function submit() {
  if (!user.authed) {
    showToast('请先完成实名认证');
    router.push('/profile');
    return;
  }
  if (!currentOption.value) {
    showToast('请选择档位');
    return;
  }
  if (!agreed.value) {
    showToast('请先阅读并同意协议');
    return;
  }
  if (currentOption.value.payAmount > 0 && !assertPayAllowed()) return;

  submitting.value = true;
  try {
    const order = await purchaseApi.create({
      levelId: currentOption.value.levelId,
      activityId: currentActivityId.value,
      agreed: true
    });
    user.loadProfile().catch(() => {});
    if (order.payNo) {
      router.replace({ path: `/pay/${order.payNo}`, query: { amount: order.amount } });
      return;
    }
    await showDialog({
      title: '办理成功',
      message: '业务已生效，可在记录时间线中查看明细。',
      confirmButtonText: '查看记录'
    });
    router.replace('/timeline');
  } catch (e) {
    // 错误已提示
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.purchase-page {
  padding-bottom: 96px;
}

.current-level {
  font-size: 19px;
  font-weight: 600;
  margin-top: 2px;
}

.right-info {
  text-align: right;
}

.source-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.source-item {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 10px 12px;
  background: #fafbfc;
}

.source-item.active {
  border-color: var(--brand);
  background: #eef4ff;
}

.source-title {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 2px;
}

.option-item {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
  background: #fafbfc;
}

.option-item:last-child {
  margin-bottom: 0;
}

.option-item.active {
  border-color: var(--brand);
  background: #eef4ff;
}

.option-name {
  font-size: 15px;
  font-weight: 600;
}

.agreement-card a {
  color: var(--brand);
}

.pay-amount {
  font-size: 18px;
}

.agreement-popup {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
}

.popup-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 10px;
}

.popup-content {
  flex: 1;
  overflow-y: auto;
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 13px;
  color: #444;
}
</style>
