<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import html2canvas from 'html2canvas';
import logoUrl from '../assets/logo-white.png';
import { storeApi } from '../api';

const router = useRouter();
const loading = ref(true);
const saving = ref(false);
const qr = ref(null);
const posterRef = ref(null);

onMounted(async () => {
  try {
    qr.value = await storeApi.factoryInviteQr();
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
    showToast('注册链接已复制');
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
    const link = document.createElement('a');
    link.href = canvas.toDataURL('image/png');
    link.download = '发士莲-厂家邀请门店.png';
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
    <van-nav-bar title="邀请门店" />
    <div class="body">
      <van-loading v-if="loading" class="loading" />

      <template v-else-if="qr">
        <div ref="posterRef" class="poster">
          <div class="poster-top">
            <img class="poster-logo" :src="logoUrl" alt="发士莲" />
            <div class="poster-slogan">欢迎您加入发士莲草本养发</div>
          </div>

          <div class="poster-card">
            <img class="poster-qr" :src="qr.qrCode" alt="注册二维码" />
          </div>

          <div class="poster-foot">
            <div class="foot-line">发士莲订货商城</div>
            <div class="foot-url">mall.76779.cn/h5</div>
          </div>
        </div>

        <div class="actions">
          <van-button type="primary" block round :loading="saving" @click="savePoster">保存邀请图</van-button>
          <van-button class="mt-10" block round plain @click="copyLink">复制注册链接</van-button>
          <van-button class="mt-10" block round plain @click="router.back()">返回</van-button>
        </div>

        <div class="tips">
          <van-icon name="info-o" />
          <span>保存图片后可直接转发给门店老板；对方扫码注册后归属本厂家，需在后台完成加盟才能下单</span>
        </div>
      </template>

      <van-empty v-else description="二维码生成失败，请返回重试" />
    </div>
  </div>
</template>

<style scoped>
.invite-page {
  min-height: 100vh;
  background: #eef2ec;
}

.body {
  padding: 12px 16px calc(24px + env(safe-area-inset-bottom));
}

.loading {
  display: block;
  text-align: center;
  padding: 60px 0;
}

.poster {
  position: relative;
  padding: 34px 20px 26px;
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
  margin-top: 20px;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 1px;
}


.poster-card {
  position: relative;
  margin-top: 26px;
  padding: 20px;
  background: #fff;
  border-radius: 16px;
  text-align: center;
}


.poster-qr {
  width: 192px;
  height: 192px;
  margin: 0 auto;
  display: block;
  border-radius: 10px;
}





.poster-foot {
  position: relative;
  margin-top: 26px;
  text-align: center;
  color: rgba(255, 255, 255, 0.6);
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
  margin-top: 20px;
}

.mt-10 {
  margin-top: 10px;
}

.tips {
  margin-top: 14px;
  display: flex;
  gap: 6px;
  align-items: flex-start;
  font-size: 12px;
  color: #8a9099;
  line-height: 1.6;
  padding: 0 4px;
}
</style>
