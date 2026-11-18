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
      <div class="card balance-card" @click="$router.push('/wallet')">
        <div class="balance-item">
          <div class="balance-value">{{ fenToYuan(wallet.depositBalance) }}</div>
          <div class="muted small">预存余额</div>
        </div>
        <div class="balance-item">
          <div class="balance-value">{{ fenToYuan(wallet.commissionBalance) }}</div>
          <div class="muted small">佣金余额</div>
        </div>
        <div class="balance-item">
          <div class="balance-value">{{ fenToYuan(wallet.availableCommission) }}</div>
          <div class="muted small">可提现</div>
        </div>
      </div>

      <van-cell-group inset class="menu">
        <van-cell title="门店资料与实名" icon="contact-o" is-link to="/profile" />
        <van-cell title="收款卡" icon="credit-pay" is-link to="/profile" />
        <van-cell title="邀请好友" icon="qr" is-link to="/invite" />
        <van-cell title="我的团队" icon="friends-o" is-link to="/team" />
        <van-cell title="团队业绩" icon="chart-trending-o" is-link to="/performance" />
        <van-cell title="记录时间线" icon="notes-o" is-link to="/timeline" />
        <van-cell title="站内消息" icon="bell" is-link to="/notices" :value="unread > 0 ? `${unread} 条未读` : ''" />
      </van-cell-group>

      <van-cell-group inset class="menu">
        <van-cell title="加盟 / 预存" icon="shop-o" is-link to="/purchase" />
        <van-cell title="我的订单" icon="orders-o" is-link to="/orders" />
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
import { noticeApi, walletApi } from '../api';
import { useCartStore } from '../stores/cart';
import { useUserStore } from '../stores/user';
import { fenToYuan } from '../utils/format';

const router = useRouter();
const user = useUserStore();
const cart = useCartStore();
const wallet = ref({ depositBalance: 0, commissionBalance: 0, availableCommission: 0 });
const unread = ref(0);
const profile = computed(() => user.profile);
const shortName = computed(() => {
  const name = profile.value ? (profile.value.name || profile.value.nickname || '') : '';
  return name ? name.slice(0, 1) : '店';
});

onMounted(async () => {
  try {
    if (!user.profile) await user.loadProfile();
    const [walletData, unreadCount] = await Promise.all([walletApi.wallet(), noticeApi.unreadCount()]);
    wallet.value = walletData;
    unread.value = Number(unreadCount) || 0;
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

.balance-card {
  display: flex;
  padding: 14px 6px;
}

.balance-item {
  flex: 1;
  text-align: center;
}

.balance-value {
  font-size: 17px;
  font-weight: 600;
  margin-bottom: 4px;
}

.menu {
  margin: 0 0 12px;
}

.logout {
  padding: 24px 16px 8px;
}
</style>
