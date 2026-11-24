<template>
  <div class="page with-tab">
    <div class="hero">
      <div class="flex-between">
        <div>
          <div class="store-name">{{ profile ? (profile.name || profile.nickname || profile.phone) : '门店用户' }}</div>
          <div class="level-line">
            <span v-if="user.joined">{{ profile.levelName || `档位 ${profile.levelId}` }}</span>
            <span v-else>尚未加盟</span>
            <span v-if="profile && profile.authStatus !== 1" class="badge">未实名</span>
          </div>
        </div>
        <van-icon name="bell" size="22" :badge="unread > 0 ? unread : ''" @click="$router.push('/notices')" />
      </div>

      <div class="wallet-row" @click="$router.push('/wallet')">
        <div class="wallet-item">
          <div class="wallet-value">{{ fenToYuan(wallet.depositBalance) }}</div>
          <div class="wallet-label">预存余额</div>
        </div>
        <div class="divider" />
        <div class="wallet-item">
          <div class="wallet-value">{{ fenToYuan(wallet.commissionBalance) }}</div>
          <div class="wallet-label">佣金余额</div>
        </div>
        <div class="divider" />
        <div class="wallet-item">
          <div class="wallet-value">{{ fenToYuan(wallet.availableCommission) }}</div>
          <div class="wallet-label">可提佣金</div>
        </div>
      </div>
    </div>

    <div class="page-body">
      <div v-if="profile" class="card action-card">
        <template v-if="profile.authStatus !== 1">
          <div>
            <div class="action-title">完成实名认证</div>
            <div class="muted small">实名后方可加盟与下单</div>
          </div>
          <van-button size="small" type="primary" round @click="$router.push('/auth-verify')">去实名</van-button>
        </template>
        <template v-else-if="!user.joined">
          <div>
            <div class="action-title">选择档位储值</div>
            <div class="muted small">储值后可订货并享受团队提成</div>
          </div>
          <van-button size="small" type="primary" round @click="$router.push('/purchase')">去储值</van-button>
        </template>
        <template v-else>
          <div>
            <div class="action-title">预存 / 升级</div>
            <div class="muted small">预存余额可直接抵扣订单金额</div>
          </div>
          <van-button size="small" type="primary" round @click="$router.push('/purchase')">去预存</van-button>
        </template>
      </div>

      <div class="card">
        <div class="card-title">快捷入口</div>
        <van-grid :column-num="4" :border="false" clickable>
          <van-grid-item icon="shop-o" text="预存储值" @click="$router.push('/purchase')" />
          <van-grid-item icon="apps-o" text="选购商品" @click="$router.push('/products')" />
          <van-grid-item icon="balance-pay" text="我的钱包" @click="$router.push('/wallet')" />
          <van-grid-item icon="cash-back-record" text="提现" @click="$router.push('/withdraw')" />
          <van-grid-item icon="friends-o" text="我的团队" @click="$router.push('/team')" />
          <van-grid-item icon="chart-trending-o" text="团队业绩" @click="$router.push('/performance')" />
          <van-grid-item icon="notes-o" text="记录" @click="$router.push('/timeline')" />
          <van-grid-item icon="qr" text="邀请好友" @click="$router.push('/invite')" />
        </van-grid>
      </div>

      <div v-if="activities.length" class="card">
        <div class="card-title">预存活动</div>
        <div v-for="item in activities" :key="item.id" class="activity" @click="$router.push('/purchase')">
          <div>
            <div>{{ item.title }}</div>
            <div class="muted small">{{ item.startTime ? formatTime(item.startTime) : '' }} 至 {{ item.endTime ? formatTime(item.endTime) : '' }}</div>
          </div>
          <van-icon name="arrow" color="#8a9099" />
        </div>
      </div>

      <div class="card">
        <div class="card-title">
          推荐商品
          <span class="muted small" @click="$router.push('/products')">查看全部</span>
        </div>
        <ProductCard v-for="item in recommendProducts" :key="item.id" :product="item" />
        <div v-if="!recommendProducts.length" class="text-empty">暂无推荐商品</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import ProductCard from '../components/ProductCard.vue';
import { noticeApi, productApi, rechargeApi, walletApi } from '../api';
import { useUserStore } from '../stores/user';
import { fenToYuan, formatTime } from '../utils/format';

const user = useUserStore();
const wallet = ref({ depositBalance: 0, commissionBalance: 0, availableCommission: 0 });
const unread = ref(0);
const activities = ref([]);
const products = ref([]);

const profile = computed(() => user.profile);
const recommendProducts = computed(() => products.value.filter((p) => p.recommend === 1).slice(0, 3));

onMounted(async () => {
  try {
    if (!user.profile) await user.loadProfile();
    const [walletData, unreadData, activityData, productData] = await Promise.all([
      walletApi.wallet(),
      noticeApi.unreadCount(),
      rechargeApi.list(),
      productApi.list()
    ]);
    wallet.value = walletData;
    unread.value = Number(unreadData) || 0;
    activities.value = activityData || [];
    products.value = (productData || []).slice(0, 8);
  } catch (e) {
    // 错误已由请求层提示
  }
});
</script>

<style scoped>
.hero {
  padding: 22px 18px 26px;
  background: linear-gradient(150deg, #2b7cf5 0%, #1f6feb 45%, #0f4fbf 100%);
  color: #fff;
  border-radius: 0 0 22px 22px;
}

.store-name {
  font-size: 19px;
  font-weight: 600;
  margin-bottom: 6px;
}

.level-line {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.82);
  display: flex;
  align-items: center;
  gap: 6px;
}

.badge {
  padding: 1px 6px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.2);
}

.wallet-row {
  display: flex;
  align-items: center;
  margin-top: 20px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 12px;
  padding: 12px 6px;
}

.wallet-item {
  flex: 1;
  text-align: center;
}

.wallet-value {
  font-size: 17px;
  font-weight: 600;
}

.wallet-label {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.75);
  margin-top: 3px;
}

.divider {
  width: 1px;
  height: 26px;
  background: rgba(255, 255, 255, 0.22);
}

.action-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.action-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 3px;
}

.activity {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #f2f3f5;
}

.activity:last-child {
  border-bottom: none;
}
</style>
