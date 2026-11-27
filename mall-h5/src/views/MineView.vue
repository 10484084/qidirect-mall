<template>
  <div class="page with-tab">
    <div class="header">
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
        <van-cell title="修改手机号" icon="phone-o" is-link @click="openPhone" />
        <van-cell title="修改登录密码" icon="lock" is-link to="/change-password" />
      </van-cell-group>

      <van-popup v-model:show="phoneVisible" position="bottom" round>
        <div class="phone-box">
          <div class="phone-title">修改手机号</div>
          <div class="phone-desc">验证码发送到当前手机号 {{ maskedPhone }}</div>
          <van-field v-model="phoneForm.code" type="digit" maxlength="6" label="验证码" placeholder="原手机号收到的验证码">
            <template #button>
              <van-button size="small" plain type="primary" :disabled="counting > 0" @click="sendPhoneCode">
                {{ counting > 0 ? counting + 's' : '获取验证码' }}
              </van-button>
            </template>
          </van-field>
          <van-field v-model="phoneForm.newPhone" type="tel" maxlength="11" label="新手机号" placeholder="请输入新手机号" />
          <van-button class="phone-btn" type="primary" block round :loading="phoneSaving" @click="doChangePhone">
            确认修改
          </van-button>
          <van-button class="phone-btn" block round plain @click="phoneVisible = false">取消</van-button>
        </div>
      </van-popup>



      <div class="logout">
        <van-button block round plain type="danger" @click="onLogout">退出登录</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import { storeApi } from '../api';
import { useCartStore } from '../stores/cart';
import { useUserStore } from '../stores/user';

const router = useRouter();
const user = useUserStore();
const cart = useCartStore();
const profile = computed(() => user.profile);

const phoneVisible = ref(false);
const phoneSaving = ref(false);
const counting = ref(0);
const phoneForm = reactive({ code: '', newPhone: '' });
let phoneTimer = null;

const maskedPhone = computed(() => {
  const p = (profile.value && profile.value.phone) || '';
  return p.length === 11 ? p.slice(0, 3) + '****' + p.slice(7) : '未绑定';
});

function openPhone() {
  phoneForm.code = '';
  phoneForm.newPhone = '';
  phoneVisible.value = true;
}

async function sendPhoneCode() {
  await storeApi.sendChangePhoneCode();
  showToast('验证码已发送到原手机号');
  counting.value = 60;
  clearInterval(phoneTimer);
  phoneTimer = setInterval(() => {
    counting.value -= 1;
    if (counting.value <= 0) clearInterval(phoneTimer);
  }, 1000);
}

async function doChangePhone() {
  if (!phoneForm.code || !phoneForm.newPhone) {
    showToast('请填写验证码与新手机号');
    return;
  }
  phoneSaving.value = true;
  try {
    await storeApi.changePhone({ code: phoneForm.code, newPhone: phoneForm.newPhone });
    showToast('手机号已修改');
    phoneVisible.value = false;
    await user.loadProfile();
  } finally {
    phoneSaving.value = false;
  }
}

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
.phone-box {
  padding: 22px 16px calc(20px + env(safe-area-inset-bottom));
}

.phone-title {
  font-size: 17px;
  font-weight: 600;
  text-align: center;
}

.phone-desc {
  margin: 6px 0 14px;
  font-size: 12px;
  color: var(--text-sub);
  text-align: center;
}

.phone-btn {
  margin-top: 12px;
}

.header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px 18px;
  background: linear-gradient(150deg, #3d7a4c, #245234);
  color: #fff;
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
