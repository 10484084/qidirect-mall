<script setup>
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant';
import { storeApi } from '../api';
import { useUserStore } from '../stores/user';

const router = useRouter();
const userStore = useUserStore();

const inviteVisible = ref(false);
const invite = ref(null);
const inviteLoading = ref(false);

watch(inviteVisible, async (show) => {
  if (!show || invite.value) return;
  inviteLoading.value = true;
  try {
    invite.value = await storeApi.factoryInviteQr();
  } catch (e) {
    invite.value = null;
  } finally {
    inviteLoading.value = false;
  }
});

async function copyInvite() {
  const url = invite.value && invite.value.inviteUrl;
  if (!url) return;
  try {
    await navigator.clipboard.writeText(url);
    showSuccessToast('链接已复制');
  } catch (e) {
    showFailToast('复制失败，请长按手动复制');
  }
}

const initial = computed(() => (userStore.realName || '管').slice(0, 1).toUpperCase());
const isSuper = computed(() => userStore.perms.includes('*'));

async function logout() {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确定退出当前账号吗？' });
  } catch (e) {
    return;
  }
  userStore.logout();
  router.replace('/login');
}

async function clearCache() {
  try {
    await showConfirmDialog({ title: '清除缓存', message: '将清除本地缓存数据（不影响登录状态），确定继续？' });
  } catch (e) {
    return;
  }
  const keep = {
    token: localStorage.getItem('mall_admin_m_token'),
    profile: localStorage.getItem('mall_admin_m_profile'),
    account: localStorage.getItem('mall_admin_m_account')
  };
  localStorage.clear();
  if (keep.token) localStorage.setItem('mall_admin_m_token', keep.token);
  if (keep.profile) localStorage.setItem('mall_admin_m_profile', keep.profile);
  if (keep.account) localStorage.setItem('mall_admin_m_account', keep.account);
  showSuccessToast('缓存已清除');
}
</script>

<template>
  <div class="page page-plain">
    <div class="head">
      <div class="avatar">{{ initial }}</div>
      <div class="info">
        <div class="name">{{ userStore.realName || '管理员' }}</div>
        <div class="account">账号：{{ userStore.account || '-' }}</div>
      </div>
    </div>

    <div class="card stats">
      <div class="stat">
        <div class="stat-value">{{ isSuper ? '全部' : userStore.perms.length }}</div>
        <div class="stat-label">权限项</div>
      </div>
      <div class="stat">
        <div class="stat-value">v1.0</div>
        <div class="stat-label">版本</div>
      </div>
    </div>

    <van-cell-group inset class="group">
      <van-cell title="提现审核" is-link to="/withdraws" />
      <van-cell title="邀请门店" is-link @click="inviteVisible = true" />
    </van-cell-group>

    <van-popup v-model:show="inviteVisible" position="bottom" round>
      <div class="invite-box">
        <div class="invite-title">邀请门店使用</div>
        <div class="invite-desc">门店扫码注册，注册后归属本厂家</div>
        <van-loading v-if="inviteLoading" class="invite-loading" size="22" vertical>生成中...</van-loading>
        <template v-else-if="invite">
          <img v-if="invite.qrCode" class="invite-qr" :src="invite.qrCode" alt="邀请二维码" />
          <div class="invite-url">{{ invite.inviteUrl }}</div>
          <van-button type="primary" block round @click="copyInvite">复制注册链接</van-button>
        </template>
        <van-empty v-else description="生成失败，请重试" />
        <van-button class="invite-close" plain block round @click="inviteVisible = false">关闭</van-button>
      </div>
    </van-popup>

    <van-cell-group inset class="group">
      <van-cell title="清除本地缓存" is-link @click="clearCache" />
    </van-cell-group>

    <van-cell-group inset class="group">
      <van-cell title="退出登录" is-link class="danger-cell" @click="logout" />
    </van-cell-group>

    <p class="tip">厂家后台 · 数据 / 商品 / 订单 / 门店 / 提现</p>
  </div>
</template>

<style scoped>
.invite-box {
  padding: 22px 20px calc(20px + env(safe-area-inset-bottom));
  text-align: center;
}

.invite-title {
  font-size: 17px;
  font-weight: 600;
}

.invite-desc {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-sub);
}

.invite-loading {
  padding: 36px 0;
}

.invite-qr {
  width: 200px;
  height: 200px;
  margin: 16px auto 10px;
  display: block;
  border-radius: 10px;
}

.invite-url {
  margin-bottom: 16px;
  font-size: 11px;
  color: #9aa0a8;
  word-break: break-all;
}

.invite-close {
  margin-top: 10px;
}

.head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 26px 18px 22px;
}

.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand), var(--brand-dark));
  color: #fff;
  font-size: 24px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 16px rgba(31, 111, 235, 0.25);
}

.name {
  font-size: 19px;
  font-weight: 600;
}

.account {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-sub);
}

.stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  margin: 0 12px 8px;
  text-align: center;
}

.stat-value {
  font-size: 16px;
  font-weight: 600;
}

.stat-label {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-sub);
}

.group {
  margin: 12px 8px 0;
}

.danger-cell :deep(.van-cell__title) {
  color: #d93025;
}

.tip {
  margin: 24px 0 0;
  text-align: center;
  font-size: 12px;
  color: #b7bcc4;
}
</style>
