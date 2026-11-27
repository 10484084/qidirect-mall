<script setup>
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showFailToast, showSuccessToast } from 'vant';
import { authApi } from '../api';
import { useUserStore } from '../stores/user';
import logoUrl from '../assets/logo.png';
import { getLastAccount } from '../utils/token';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const loading = ref(false);
const resetVisible = ref(false);
const resetting = ref(false);
const counting = ref(0);
const resetForm = reactive({ phone: '', code: '', newPassword: '' });
let timer = null;

function openReset() {
  resetForm.phone = '';
  resetForm.code = '';
  resetForm.newPassword = '';
  resetVisible.value = true;
}

async function sendCode() {
  if (!/^1[3-9]\d{9}$/.test(resetForm.phone)) {
    showFailToast('请输入正确的手机号');
    return;
  }
  await authApi.sendResetCode({ phone: resetForm.phone });
  showSuccessToast('若该手机号已绑定，验证码已发送');
  counting.value = 60;
  clearInterval(timer);
  timer = setInterval(() => {
    counting.value -= 1;
    if (counting.value <= 0) clearInterval(timer);
  }, 1000);
}

async function doReset() {
  if (!resetForm.phone || !resetForm.code || !resetForm.newPassword) {
    showFailToast('请填写完整');
    return;
  }
  resetting.value = true;
  try {
    await authApi.resetPassword({
      phone: resetForm.phone,
      code: resetForm.code,
      newPassword: resetForm.newPassword
    });
    showSuccessToast('密码已重置，请用新密码登录');
    resetVisible.value = false;
    resetForm.phone = '';
    resetForm.code = '';
    resetForm.newPassword = '';
  } finally {
    resetting.value = false;
  }
}
const form = reactive({
  account: getLastAccount(),
  password: ''
});

async function onSubmit() {
  if (!form.account.trim()) {
    showFailToast('请输入账号');
    return;
  }
  if (!form.password) {
    showFailToast('请输入密码');
    return;
  }
  loading.value = true;
  try {
    await userStore.login(form.account.trim(), form.password);
    showSuccessToast('登录成功');
    const redirect = route.query.redirect;
    await router.replace(typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/dashboard');
  } catch (e) {
    /* 错误提示由请求拦截器统一处理 */
  } finally {
    loading.value = false;
  }
}

function toDesktop() {
  // desktop=1 让 Nginx 不再把手机 UA 重定向回移动版
  window.location.href = `${window.location.origin}/admin-web/?desktop=1#/login`;
}
</script>

<template>
  <div class="login">
    <div class="brand">
      <img class="brand-logo" :src="logoUrl" alt="发士莲 Fadsline" />
      <h1>厂家后台</h1>
      <p>移动版 · 订单 / 门店 / 提现</p>
    </div>

    <div class="panel">
      <van-form @submit="onSubmit">
        <van-cell-group inset>
          <van-field
            v-model="form.account"
            name="account"
            label="账号"
            placeholder="请输入登录账号"
            autocomplete="username"
            clearable
          />
          <van-field
            v-model="form.password"
            type="password"
            name="password"
            label="密码"
            placeholder="请输入登录密码"
            autocomplete="current-password"
          />
        </van-cell-group>

        <div class="actions">
          <van-button type="primary" block round native-type="submit" :loading="loading" loading-text="登录中...">
            登录
          </van-button>
        </div>
      </van-form>
    </div>

    <div class="footer">
      <span @click="openReset">忘记密码</span>
      <span class="sep">·</span>
      <span @click="toDesktop">使用电脑版后台</span>
    </div>

    <van-popup v-model:show="resetVisible" position="bottom" round>
      <div class="reset">
        <div class="reset-title">重置密码</div>
        <div class="reset-desc">验证码发送到账号绑定的手机号</div>
        <van-field v-model="resetForm.phone" type="tel" maxlength="11" label="手机号" placeholder="请输入绑定手机号" />
        <van-field v-model="resetForm.code" type="digit" maxlength="6" label="验证码" placeholder="6 位验证码">
          <template #button>
            <van-button size="small" plain type="primary" :disabled="counting > 0" @click="sendCode">
              {{ counting > 0 ? counting + 's' : '获取验证码' }}
            </van-button>
          </template>
        </van-field>
        <van-field v-model="resetForm.newPassword" type="password" maxlength="32" label="新密码" placeholder="6-32 位" />
        <van-button class="reset-btn" type="primary" block round :loading="resetting" @click="doReset">
          确认重置
        </van-button>
        <van-button class="reset-btn" block round plain @click="resetVisible = false">取消</van-button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.footer .sep {
  margin: 0 8px;
  color: #c8cdd4;
}

.reset {
  padding: 22px 16px calc(20px + env(safe-area-inset-bottom));
}

.reset-title {
  font-size: 17px;
  font-weight: 600;
  text-align: center;
}

.reset-desc {
  margin: 6px 0 14px;
  font-size: 12px;
  color: var(--text-sub);
  text-align: center;
}

.reset-btn {
  margin-top: 12px;
}

.login {
  min-height: 100vh;
  background: linear-gradient(180deg, #e7f0e8 0%, #f5f7f4 42%);
  padding: 0 0 24px;
}

.brand {
  padding: 56px 24px 28px;
  text-align: center;
}

.brand-logo {
  display: block;
  width: 180px;
  height: auto;
  margin: 0 auto 10px;
}

.brand h1 {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 600;
}

.brand p {
  margin: 0;
  font-size: 13px;
  color: var(--text-sub);
}

.panel {
  padding: 0 4px;
}

.actions {
  padding: 20px 16px 0;
}

.footer {
  margin-top: 28px;
  text-align: center;
  font-size: 13px;
  color: var(--brand);
}
</style>
