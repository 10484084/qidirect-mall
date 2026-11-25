<template>
  <div class="page with-tab">
    <div class="header">
      <div class="avatar">
        <img v-if="profile && profile.avatar" :src="profile.avatar" alt="avatar" />
        <span v-else>{{ shortName }}</span>
      </div>
      <div class="info">
        <div class="name">{{ profile ? (profile.name || profile.nickname || profile.phone) : '门店用户' }}</div>
        <div class="muted small">{{ profile ? profile.phone : '' }}</div>
        <div class="meta-row">
          <span class="level-chip">{{ user.joined ? profile.levelName : '未储值' }}</span>
          <span class="auth-state" :class="user.authed ? 'is-ok' : 'is-warn'">
            <van-icon :name="user.authed ? 'passed' : 'info-o'" size="12" />
            {{ user.authed ? '已实名' : '未实名' }}
          </span>
        </div>
      </div>
      <van-icon name="setting-o" size="20" @click="$router.push('/profile')" />
    </div>

    <div class="page-body">
      <van-cell-group inset class="menu">
        <van-cell title="我的钱包" icon="gold-coin-o" is-link to="/wallet" />
        <van-cell title="我的资料" icon="contact-o" is-link to="/profile" />
        <van-cell title="邀请好友" icon="qr" is-link to="/invite" />
        <van-cell title="我的团队" icon="friends-o" is-link to="/team" />
        <van-cell title="团队业绩" icon="chart-trending-o" is-link to="/performance" />
        <van-cell title="历史记录" icon="notes-o" is-link to="/timeline" />
        <van-cell title="修改登录密码" icon="lock" is-link to="/change-password" />
      </van-cell-group>

      <div class="logout">
        <van-button block round plain type="danger" @click="onLogout">退出登录</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useCartStore } from '../stores/cart';
import { useUserStore } from '../stores/user';

const router = useRouter();
const user = useUserStore();
const cart = useCartStore();
const profile = computed(() => user.profile);
const shortName = computed(() => {
  const name = profile.value ? (profile.value.name || profile.value.nickname || '') : '';
  return name ? name.slice(0, 1) : '店';
});

onMounted(async () => {
  try {
    if (!user.profile) await user.loadProfile();
  } catch (e) {
    // 错误已提示
  }
});

function onLogout() {
  user.logout();
  cart.reset();
  router.replace('/login');
}
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px 18px;
  background: linear-gradient(150deg, #3d7a4c, #245234);
  color: #fff;
}

.avatar {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.2);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.info {
  flex: 1;
  min-width: 0;
}

.name {
  font-size: 17px;
  font-weight: 600;
  margin-bottom: 3px;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}

.level-chip {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.45);
  color: #fff;
  font-size: 12px;
  line-height: 16px;
  font-weight: 500;
}

.auth-state {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  line-height: 16px;
  color: rgba(255, 255, 255, 0.85);
}

.auth-state.is-ok {
  color: #d8edc6;
}

.auth-state.is-warn {
  color: #ffe0a3;
}

.menu {
  margin: 0 0 12px;
}

.logout {
  padding: 24px 16px 8px;
}
</style>
