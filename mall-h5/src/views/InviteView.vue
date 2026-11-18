<template>
  <div class="page invite-page">
    <PageHeader title="邀请好友" />

    <div class="page-body">
      <van-loading v-if="loading" class="loading" />
      <template v-else>
        <div v-if="!joined" class="card locked">
          <van-icon name="qr" size="40" color="#9aa4b2" />
          <div class="locked-title">加盟后可生成邀请二维码</div>
          <div class="muted small">邀请下级加盟，可获得两级提成与团队业绩奖。</div>
          <van-button class="mt-12" type="primary" round size="small" @click="$router.push('/purchase')">
            去加盟
          </van-button>
        </div>

        <template v-else-if="qr">
          <div class="card qr-card">
            <img :src="qr.qrCode" alt="邀请二维码" class="qr-img" />
            <div class="invite-code">邀请码：{{ qr.inviteCode }}</div>
            <div class="muted small">让好友使用微信扫码，注册后自动绑定为您的下级</div>
          </div>

          <div class="card">
            <div class="card-title">邀请链接</div>
            <div class="muted small link-text">{{ qr.inviteUrl }}</div>
            <van-button class="mt-12" type="primary" plain block round @click="copyLink">复制邀请链接</van-button>
          </div>

          <div class="card tip">
            <div class="card-title">提成与业绩说明</div>
            <ul>
              <li>首次加盟：上级任意档位均可获得一级 10% / 二级 3% 提成。</li>
              <li>补差、预存、订货支付：仅 5 万档门店可按比例获得提成。</li>
              <li>团队业绩奖：仅最高档位门店参与，按结算时整棵子树汇总。</li>
            </ul>
          </div>
        </template>
      </template>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { showToast } from 'vant';
import PageHeader from '../components/PageHeader.vue';
import { storeApi } from '../api';
import { useUserStore } from '../stores/user';

const user = useUserStore();
const loading = ref(true);
const joined = ref(false);
const qr = ref(null);

onMounted(async () => {
  try {
    if (!user.profile) await user.loadProfile();
    joined.value = user.joined;
    if (!joined.value) return;
    qr.value = await storeApi.inviteQr();
  } catch (e) {
    qr.value = null;
  } finally {
    loading.value = false;
  }
});

async function copyLink() {
  if (!qr.value) return;
  try {
    await navigator.clipboard.writeText(qr.value.inviteUrl);
    showToast('邀请链接已复制');
  } catch (e) {
    showToast('复制失败，请长按链接手动复制');
  }
}
</script>

<style scoped>
.loading {
  display: block;
  text-align: center;
  padding: 60px 0;
}

.locked {
  text-align: center;
  padding: 36px 20px;
}

.locked-title {
  font-size: 16px;
  font-weight: 600;
  margin: 12px 0 6px;
}

.qr-card {
  text-align: center;
  padding: 24px 16px;
}

.qr-img {
  width: 220px;
  height: 220px;
  object-fit: contain;
  margin-bottom: 12px;
}

.invite-code {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 6px;
}

.link-text {
  word-break: break-all;
  line-height: 1.6;
}

.tip li {
  font-size: 12px;
  color: #5b6470;
  line-height: 1.8;
}

.tip ul {
  margin: 0;
  padding-left: 18px;
}
</style>
