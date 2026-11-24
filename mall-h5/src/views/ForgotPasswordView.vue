<template>
  <div class="forgot-page">
    <PageHeader title="忘记密码" />

    <div class="page-body">
      <div class="card form-card">
        <p class="muted small tip">输入注册手机号，用短信验证码重置登录密码。</p>
        <van-form @submit="onSubmit">
          <van-cell-group inset class="form-group">
            <van-field
              v-model="form.phone"
              label="手机号"
              type="digit"
              maxlength="11"
              clearable
              placeholder="请输入注册手机号"
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
            <van-field
              v-model="form.newPassword"
              type="password"
              label="新密码"
              maxlength="32"
              placeholder="6-32 位，不能是 admin"
              :rules="[{ required: true, message: '请输入新密码' }]"
            />
            <van-field
              v-model="form.confirmPassword"
              type="password"
              label="确认密码"
              maxlength="32"
              placeholder="再次输入新密码"
              :rules="[{ required: true, message: '请再次输入新密码' }]"
            />
          </van-cell-group>

          <div class="submit-box">
            <van-button type="primary" block round native-type="submit" :loading="submitting">
              重置密码
            </van-button>
          </div>
        </van-form>

        <p class="back-tip">
          记起来了？<span class="link" @click="router.replace('/login')">返回登录</span>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import PageHeader from '../components/PageHeader.vue';
import { authApi } from '../api';

const route = useRoute();
const router = useRouter();

const form = reactive({
  phone: typeof route.query.phone === 'string' ? route.query.phone : '',
  code: '',
  newPassword: '',
  confirmPassword: ''
});
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
  if (form.newPassword.length < 6) {
    showToast('密码至少 6 位');
    return;
  }
  if (form.newPassword !== form.confirmPassword) {
    showToast('两次输入的密码不一致');
    return;
  }
  submitting.value = true;
  try {
    await authApi.resetPassword({
      phone,
      code: form.code.trim(),
      newPassword: form.newPassword
    });
    showToast('密码已重置，请重新登录');
    router.replace('/login');
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
.forgot-page {
  min-height: 100vh;
  background: var(--page-bg);
}

.tip {
  margin: 4px 16px 12px;
  line-height: 1.5;
}

.card {
  background: #fff;
  border-radius: 14px;
  padding: 8px 0 16px;
}

.form-group {
  margin-top: 4px;
}

.submit-box {
  padding: 20px 16px 0;
}

.back-tip {
  margin: 14px 16px 0;
  text-align: center;
  font-size: 13px;
  color: var(--text-sub);
}

.link {
  color: var(--brand);
}
</style>
