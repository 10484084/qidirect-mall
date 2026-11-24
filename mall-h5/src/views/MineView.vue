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
        <div class="tags">
          <van-tag v-if="user.joined" type="primary" plain>{{ profile.levelName }}</van-tag>
          <van-tag v-else plain>未加盟</van-tag>
          <van-tag :type="user.authed ? 'success' : 'warning'" plain>
            {{ user.authed ? '已实名' : '未实名' }}
          </van-tag>
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
  background: linear-gradient(150deg, #2b7cf5, #0f4fbf);
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

.tags {
  display: flex;
  gap: 6px;
  margin-top: 6px;
}

.menu {
  margin: 0 0 12px;
}

.logout {
  padding: 24px 16px 8px;
}
</style>
