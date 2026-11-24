<template>
  <div class="register-page">
    <div class="brand">
      <div class="logo">店</div>
      <h2>门店注册</h2>
      <p class="muted small">扫码注册后自动绑定上级门店</p>
    </div>

    <div class="card form-card">
      <van-form @submit="onSubmit">
        <van-cell-group inset class="form-group">
          <van-field
            v-if="inviteCode"
            :model-value="inviteCode"
            label="邀请码"
            readonly
          />
          <van-field
            v-model="form.phone"
            label="手机号"
            type="digit"
            maxlength="11"
            clearable
            placeholder="请输入手机号"
            :rules="[{ required: true, message: '请输入手机号' }]"
          />
          <van-field
            v-model="form.code"
            label="验证码"
            type="digit"
            maxlength="6"
            placeholder="请输入 6 位验证码"
            :rules="[{ required: true, message: '请输入验证码' }]"
          >
            <template #button>
              <van-button
                size="small"
                type="primary"
                plain
                round
                :loading="sending"
                :disabled="countdown > 0"
                @click.prevent="sendCode"
              >
                {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
              </van-button>
            </template>
          </van-field>
        </van-cell-group>

        <div class="submit-box">
          <van-button type="primary" block round native-type="submit" :loading="submitting">
            注册
          </van-button>
        </div>
      </van-form>

      <p class="login-tip">
        已有账号？<span class="link" @click="router.replace('/login')">去登录</span>
      </p>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import { authApi } from '../api';
import { useUserStore } from '../stores/user';

const route = useRoute();
const router = useRouter();
const user = useUserStore();

const inviteCode = ref(typeof route.query.inviteCode === 'string' ? route.query.inviteCode.toUpperCase() : '');
const form = reactive({ phone: '', code: '' });
const sending = ref(false);
const submitting = ref(false);
const countdown = ref(0);
let timer = null;

const PHONE_RE = /^1[3-9]\d{9}$/;

function startCountdown() {
  countdown.value = 60;
  timer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) {
      clearInterval(timer);
      timer = null;
    }
  }, 1000);
}

async function sendCode() {
  const phone = form.phone.trim();
  if (!PHONE_RE.test(phone)) {
    showToast('请输入正确的手机号');
    return;
  }
  sending.value = true;
  try {
    await authApi.sendSms(phone);
    showToast('验证码已发送');
    startCountdown();
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    sending.value = false;
  }
}

async function onSubmit() {
  const phone = form.phone.trim();
  if (!PHONE_RE.test(phone)) {
    showToast('请输入正确的手机号');
    return;
  }
  submitting.value = true;
  try {
    const data = await user.register({
      phone,
      code: form.code.trim(),
      inviteCode: inviteCode.value || undefined
    });
    showToast('注册成功');
    if (data && data.needChangePwd) {
      router.replace('/change-password');
      return;
    }
    router.replace('/');
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false;
  }
}

onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  padding: 48px 8px 32px;
  background: linear-gradient(180deg, #eef4ff 0%, #f6f7f9 42%);
}

.brand {
  text-align: center;
  padding: 0 20px 22px;
}

.logo {
  width: 56px;
  height: 56px;
  margin: 0 auto 12px;
  border-radius: 16px;
  background: var(--brand);
  color: #fff;
  font-size: 24px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand h2 {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.card {
  background: #fff;
  border-radius: 14px;
  margin: 0 12px;
  padding: 8px 0 16px;
  box-shadow: 0 2px 10px rgba(31, 111, 235, 0.06);
}

.form-group {
  margin-top: 8px;
}

.submit-box {
  padding: 20px 16px 0;
}

.login-tip {
  margin: 14px 16px 0;
  text-align: center;
  font-size: 13px;
  color: var(--text-sub);
}

.link {
  color: var(--brand);
}
</style>
