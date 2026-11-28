<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant';
import { storeApi } from '../../api';
import { AUTH_STATUS, STORE_STATUS } from '../../utils/dict';
import { fen2yuan } from '../../utils/format';

const route = useRoute();
const router = useRouter();

const loading = ref(true);
const detail = ref(null);

const phoneVisible = ref(false);
const freezeVisible = ref(false);
const qrVisible = ref(false);
const submitting = ref(false);

const phone = ref('');
const freezeReason = ref('');
const qr = ref(null);

const profile = computed(() => (detail.value && detail.value.profile) || {});
const wallet = computed(() => (detail.value && detail.value.wallet) || {});
const statusInfo = computed(() => STORE_STATUS[profile.value.status] || { label: '-', type: 'info' });
const authInfo = computed(() => AUTH_STATUS[profile.value.authStatus] || { label: '-' });

async function load() {
  loading.value = true;
  try {
    detail.value = await storeApi.detail(route.params.id);
  } finally {
    loading.value = false;
  }
}

function openPhone() {
  phone.value = profile.value.phone || '';
  phoneVisible.value = true;
}

async function submitPhone() {
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    showFailToast('请输入正确的手机号');
    return;
  }
  submitting.value = true;
  try {
    await storeApi.resetPhone(profile.value.id, phone.value);
    showSuccessToast('手机号已重置');
    phoneVisible.value = false;
    await load();
  } finally {
    submitting.value = false;
  }
}

async function resetPwd() {
  try {
    await showConfirmDialog({
      title: '重置登录密码',
      message: '将把该门店登录密码重置为初始密码 admin，门店下次登录后需要重新修改。确定继续吗？'
    });
  } catch (e) {
    return;
  }
  submitting.value = true;
  try {
    await storeApi.resetPassword(profile.value.id);
    showSuccessToast('密码已重置为 admin');
  } finally {
    submitting.value = false;
  }
}

async function toggleFreeze() {
  if (profile.value.status === 1) {
    freezeReason.value = '';
    freezeVisible.value = true;
    return;
  }
  try {
    await showConfirmDialog({ title: '解冻门店', message: '确认解冻该门店吗？' });
  } catch (e) {
    return;
  }
  submitting.value = true;
  try {
    await storeApi.unfreeze(profile.value.id, { reason: '后台解冻' });
    showSuccessToast('已解冻');
    await load();
  } finally {
    submitting.value = false;
  }
}

async function submitFreeze() {
  if (!freezeReason.value.trim()) {
    showFailToast('请填写冻结原因');
    return;
  }
  submitting.value = true;
  try {
    await storeApi.freeze(profile.value.id, { reason: freezeReason.value.trim() });
    showSuccessToast('门店已冻结');
    freezeVisible.value = false;
    await load();
  } finally {
    submitting.value = false;
  }
}

async function showQr() {
  qr.value = await storeApi.inviteQr(profile.value.id);
  qrVisible.value = true;
}

onMounted(load);
</script>

<template>
  <div class="page-plain detail-page">

    <van-loading v-if="loading" class="loading" vertical>加载中...</van-loading>

    <template v-else-if="detail">
      <div class="head card">
        <div class="row">
          <div class="name">{{ profile.name || profile.nickname || '未命名门店' }}</div>
          <span
            class="tag-text"
            :class="{ 'tag-success': statusInfo.type === 'success', 'tag-danger': statusInfo.type === 'danger' }"
          >
            {{ statusInfo.label }}
          </span>
        </div>
        <div class="row">
          <a :href="`tel:${profile.phone}`" class="phone">{{ profile.phone }}</a>
          <span class="muted">ID {{ profile.id }}</span>
        </div>
        <div class="row">
          <span class="muted">上级：{{ profile.parentName || '无' }}</span>
          <span class="muted">{{ profile.levelName || '未开通' }}</span>
        </div>
        <div class="row">
          <span class="muted">邀请码：{{ profile.inviteCode || '-' }}</span>
          <span class="muted">加入 {{ profile.joinTime || '-' }}</span>
        </div>
      </div>

      <div class="section-title">钱包</div>
      <div class="card wallet">
        <div class="wallet-item">
          <div class="w-label">预存余额</div>
          <div class="w-value">¥{{ fen2yuan(wallet.depositBalance) }}</div>
        </div>
        <div class="wallet-item">
          <div class="w-label">佣金余额</div>
          <div class="w-value">¥{{ fen2yuan(wallet.commissionBalance) }}</div>
        </div>
        <div class="wallet-item">
          <div class="w-label">冻结金额</div>
          <div class="w-value">¥{{ fen2yuan(wallet.frozenAmount) }}</div>
        </div>
        <div class="wallet-item">
          <div class="w-label">可提佣金</div>
          <div class="w-value money">¥{{ fen2yuan(wallet.availableCommission) }}</div>
        </div>
      </div>

      <div class="section-title">门店操作</div>
      <div class="card actions">
        <van-button size="small" plain type="primary" @click="openPhone">重置手机号</van-button>
        <van-button size="small" plain type="primary" @click="resetPwd">重置密码</van-button>
        <van-button size="small" plain type="primary" @click="showQr">邀请二维码</van-button>
        <van-button
          size="small"
          plain
          :type="profile.status === 1 ? 'danger' : 'success'"
          :loading="submitting"
          @click="toggleFreeze"
        >
          {{ profile.status === 1 ? '冻结门店' : '解冻门店' }}
        </van-button>
      </div>

      <div class="section-title">实名信息</div>
      <div class="card">
        <div class="row">
          <span class="muted">实名状态</span>
          <span>{{ authInfo.label }}</span>
        </div>
        <div class="row">
          <span class="muted">真实姓名</span>
          <span>{{ profile.realName || '-' }}</span>
        </div>
        <div class="row">
          <span class="muted">身份证号</span>
          <span>{{ profile.idCardMasked || '-' }}</span>
        </div>
        <div class="row">
          <span class="muted">营业执照号</span>
          <span>{{ profile.licenseNo || '-' }}</span>
        </div>
        <div class="row">
          <span class="muted">账号关系</span>
          <span :class="{ dup: profile.duplicateFlag }">{{ profile.duplicateFlag ? '疑似重复' : '正常' }}</span>
        </div>
      </div>

      <div class="section-title">收款卡</div>
      <div class="card">
        <div class="row">
          <span class="muted">持卡人</span>
          <span>{{ profile.bankHolder || '-' }}</span>
        </div>
        <div class="row">
          <span class="muted">开户行</span>
          <span>{{ profile.bankName || '-' }}</span>
        </div>
        <div class="row">
          <span class="muted">卡号</span>
          <span>{{ profile.bankAccountMasked || '-' }}</span>
        </div>
      </div>

      <p class="tip">收款卡特批请使用电脑版后台</p>
    </template>

    <van-popup v-model:show="phoneVisible" position="bottom" round safe-area-inset-bottom>
      <div class="sheet">
        <div class="sheet-title">重置手机号</div>
        <van-cell-group inset>
          <van-field v-model="phone" label="手机号" type="tel" maxlength="11" placeholder="请输入新手机号" />
        </van-cell-group>
        <div class="sheet-actions">
          <van-button plain block round @click="phoneVisible = false">取消</van-button>
          <van-button type="primary" block round :loading="submitting" @click="submitPhone">确认</van-button>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="freezeVisible" position="bottom" round safe-area-inset-bottom>
      <div class="sheet">
        <div class="sheet-title">冻结门店</div>
        <van-cell-group inset>
          <van-field
            v-model="freezeReason"
            label="原因"
            type="textarea"
            rows="2"
            maxlength="100"
            show-word-limit
            placeholder="请填写冻结原因"
          />
        </van-cell-group>
        <div class="sheet-actions">
          <van-button plain block round @click="freezeVisible = false">取消</van-button>
          <van-button type="danger" block round :loading="submitting" @click="submitFreeze">确认冻结</van-button>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="qrVisible" round closeable class="qr-popup">
      <div v-if="qr" class="qr-box">
        <div class="sheet-title">邀请二维码</div>
        <img v-if="qr.qrCode" :src="qr.qrCode" class="qr-img" alt="邀请二维码" />
        <div class="qr-code">邀请码：{{ qr.inviteCode }}</div>
        <div class="muted qr-url">{{ qr.inviteUrl }}</div>
        <p class="muted small-tip">长按图片可保存或分享</p>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.detail-page {
  padding-bottom: 28px;
}

.loading {
  padding: 60px 0;
}

.head {
  margin: 12px;
}

.name {
  font-size: 18px;
  font-weight: 600;
}

.phone {
  color: var(--brand);
  text-decoration: none;
  font-size: 14px;
}

.card {
  margin: 0 12px 10px;
}

.section-title {
  margin: 14px 16px 8px;
}

.wallet {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px 8px;
}

.w-label {
  font-size: 12px;
  color: var(--text-sub);
}

.w-value {
  margin-top: 3px;
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.dup {
  color: #d93025;
}

.tip {
  margin: 16px;
  text-align: center;
  font-size: 12px;
  color: #b7bcc4;
}

.sheet {
  padding: 18px 0 16px;
}

.sheet-title {
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 14px;
}

.sheet-actions {
  display: flex;
  gap: 10px;
  padding: 18px 16px 0;
}

.qr-popup {
  width: 78%;
  max-width: 320px;
}

.qr-box {
  padding: 20px 18px;
  text-align: center;
}

.qr-img {
  width: 200px;
  height: 200px;
  object-fit: contain;
  margin: 0 auto;
  display: block;
}

.qr-code {
  margin-top: 10px;
  font-size: 14px;
}

.qr-url {
  margin-top: 6px;
  word-break: break-all;
  font-size: 11px;
}

.small-tip {
  margin-top: 10px;
  font-size: 11px;
}
</style>
