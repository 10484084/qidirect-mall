<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { showConfirmDialog, showSuccessToast } from 'vant';
import { useUserStore } from '../stores/user';

const router = useRouter();
const userStore = useUserStore();

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

function toDesktop() {
  // desktop=1 让 Nginx 不再把手机 UA 重定向回移动版
  window.location.href = `${window.location.origin}/admin-web/?desktop=1#/dashboard`;
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
        <div class="stat-value">移动版</div>
        <div class="stat-label">当前端</div>
      </div>
      <div class="stat">
        <div class="stat-value">v1.0</div>
        <div class="stat-label">版本</div>
      </div>
    </div>

    <van-cell-group inset class="group">
      <van-cell title="切换电脑版后台" label="表格编辑、财务对账等完整功能" is-link @click="toDesktop" />
      <van-cell title="清除本地缓存" is-link @click="clearCache" />
    </van-cell-group>

    <van-cell-group inset class="group">
      <van-cell title="退出登录" is-link class="danger-cell" @click="logout" />
    </van-cell-group>

    <p class="tip">厂家后台移动版 · 订单 / 门店 / 提现</p>
  </div>
</template>

<style scoped>
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
  grid-template-columns: repeat(3, 1fr);
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
