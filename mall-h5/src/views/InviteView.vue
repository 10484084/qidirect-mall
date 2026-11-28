<script setup>
import { computed, onMounted, ref } from 'vue';
import { showToast } from 'vant';
import html2canvas from 'html2canvas';
import PageHeader from '../components/PageHeader.vue';
import logoUrl from '../assets/logo-white.png';
import { storeApi } from '../api';
import { useUserStore } from '../stores/user';

const user = useUserStore();
const loading = ref(true);
const joined = ref(false);
const qr = ref(null);
const saving = ref(false);
const posterRef = ref(null);

const storeName = computed(() => user.profile?.name || user.profile?.nickname || '发士莲门店');
const inviteCode = computed(() => (qr.value && qr.value.inviteCode) || '');

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

async function savePoster() {
  if (!posterRef.value) return;
  saving.value = true;
  try {
    const canvas = await html2canvas(posterRef.value, {
      scale: 2,
      useCORS: true,
      backgroundColor: null,
      logging: false
    });
    const dataUrl = canvas.toDataURL('image/png');
    const link = document.createElement('a');
    link.href = dataUrl;
    link.download = `发士莲邀请-${inviteCode.value || 'poster'}.png`;
    link.click();
    showToast('图片已生成，可在相册或下载中查看');
  } catch (e) {
    showToast('生成失败，可长按二维码保存');
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div class="page invite-page">
    <PageHeader title="邀请好友" />

    <div class="page-body">
      <van-loading v-if="loading" class="loading" />

      <template v-else>
        <div v-if="!joined" class="locked-card">
          <van-icon name="qr" size="42" color="#9aa4b2" />
          <div class="locked-title">开通后可生成邀请二维码</div>
          <div class="locked-desc">邀请下级开通，可获得两级提成与团队业绩奖</div>
          <van-button class="mt-14" type="primary" round size="small" @click="$router.push('/purchase')">
            去开通
          </van-button>
        </div>

        <template v-else-if="qr">
          <div ref="posterRef" class="poster">
            <div class="poster-top">
              <img class="poster-logo" :src="logoUrl" alt="发士莲" />
              <div class="poster-slogan">诚邀您加入我的经销团队</div>
            </div>

            <div class="poster-card">
              <div class="poster-inviter">邀请人 · {{ storeName }}</div>
              <img class="poster-qr" :src="qr.qrCode" alt="邀请二维码" />
              <div class="poster-hint">微信扫码注册，自动成为我的下级</div>
              <div v-if="inviteCode" class="poster-code">邀请码 {{ inviteCode }}</div>
            </div>

            <div class="poster-foot">
              <div class="foot-line">发士莲订货商城</div>
              <div class="foot-url">mall.76779.cn/h5</div>
            </div>
          </div>

          <div class="actions">
            <van-button type="primary" block round :loading="saving" @click="savePoster">
              保存邀请图
            </van-button>
            <van-button class="mt-10" block round plain @click="copyLink">复制邀请链接</van-button>
          </div>

          <div class="tips">
            <van-icon name="info-o" />
            <span>保存后可直接发朋友圈或转发给好友，对方扫码即绑定为您下级</span>
          </div>
        </template>
      </template>
    </div>
  </div>
</template>

<style scoped>
.invite-page {
  background: #eef2ec;
}

.page-body {
  padding: 8px 16px calc(24px + env(safe-area-inset-bottom));
}

.loading {
  display: block;
  text-align: center;
  padding: 60px 0;
}

.locked-card {
  margin-top: 20px;
  padding: 40px 24px;
  background: #fff;
  border-radius: 16px;
  text-align: center;
}

.locked-title {
  margin-top: 14px;
  font-size: 16px;
  font-weight: 600;
}

.locked-desc {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-sub);
  line-height: 1.6;
}

.mt-14 {
  margin-top: 16px;
}

/* 海报 */
.poster {
  position: relative;
  padding: 28px 22px 24px;
  border-radius: 20px;
  background: linear-gradient(160deg, #326940 0%, #245234 58%, #1c4429 100%);
  box-shadow: 0 12px 32px rgba(36, 82, 52, 0.28);
  overflow: hidden;
}

.poster::after {
  content: '';
  position: absolute;
  right: -60px;
  top: -60px;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: rgba(144, 193, 86, 0.16);
}

.poster-top {
  position: relative;
  text-align: center;
  color: #fff;
}

.poster-logo {
  width: 124px;
  height: auto;
  display: block;
  margin: 0 auto;
}

.poster-slogan {
  margin-top: 14px;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 1px;
}

.poster-card {
  position: relative;
  margin-top: 20px;
  padding: 18px 18px 16px;
  background: #fff;
  border-radius: 16px;
  text-align: center;
}

.poster-inviter {
  font-size: 13px;
  font-weight: 600;
  color: #326940;
}

.poster-qr {
  width: 168px;
  height: 168px;
  margin: 12px auto 0;
  display: block;
  border-radius: 10px;
}

.poster-hint {
  margin-top: 10px;
  font-size: 12px;
  color: #8a9099;
}

.poster-code {
  margin-top: 10px;
  display: inline-block;
  padding: 5px 14px;
  border-radius: 999px;
  background: #e9f2e9;
  color: #245234;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1px;
}

.poster-foot {
  position: relative;
  margin-top: 18px;
  text-align: center;
  color: rgba(255, 255, 255, 0.66);
}

.foot-line {
  font-size: 12px;
}

.foot-url {
  margin-top: 4px;
  font-size: 10px;
  letter-spacing: 0.5px;
  font-family: ui-monospace, Menlo, monospace;
}

.actions {
  margin-top: 22px;
}

.mt-10 {
  margin-top: 10px;
}

.tips {
  margin-top: 16px;
  display: flex;
  gap: 6px;
  align-items: flex-start;
  font-size: 12px;
  color: #8a9099;
  line-height: 1.6;
  padding: 0 4px;
}
</style>
