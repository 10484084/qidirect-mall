<template>
  <div class="login-page">
    <div class="brand">
      <div class="logo">店</div>
      <h2>直销商城</h2>
      <p class="muted small">门店加盟 · 预存订货 · 团队业绩</p>
    </div>

    <div class="card form-card">
      <van-tabs v-model:active="mode" shrink>
        <van-tab title="登录" name="login" />
        <van-tab title="注册" name="register" />
      </van-tabs>

      <van-form @submit="onSubmit">
        <van-cell-group inset class="form-group">
          <van-field
            v-model="form.phone"
            type="tel"
            maxlength="11"
            label="手机号"
            placeholder="请输入手机号"
            :rules="[{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }]"
          />
          <van-field
            v-model="form.code"
            type="digit"
            maxlength="6"
            label="验证码"
            placeholder="请输入 6 位验证码"
            :rules="[{ pattern: /^\d{6}$/, message: '验证码为 6 位数字' }]"
          >
            <template #button>
              <van-button size="small" type="primary" plain :disabled="counting > 0" @click="sendCode">
                {{ counting > 0 ? `${counting}s` : '获取验证码' }}
              </van-button>
            </template>
          </van-field>
          <van-field
            v-if="mode === 'register'"
            v-model="form.inviteCode"
            label="邀请码"
            maxlength="16"
            placeholder="选填，填写后将绑定上级"
          />
          <van-field
            v-if="mode === 'register'"
            v-model="form.nickname"
            label="昵称"
            maxlength="64"
            placeholder="选填，默认使用手机号"
          />
        </van-cell-group>

        <div class="submit-box">
          <van-button type="primary" block round native-type="submit" :loading="submitting">
            {{ mode === 'login' ? '登录' : '注册并登录' }}
          </van-button>
        </div>
      </van-form>

      <p class="agreement-tip">
        登录即表示同意《用户服务协议》与《隐私政策》
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

const mode = ref(route.name === 'register' || route.query.mode === 'register' ? 'register' : 'login');
const form = reactive({
  phone: '',
  code: '',
  inviteCode: (route.query.inviteCode || route.query.code || '').toString(),
  nickname: ''
});
const counting = ref(0);
const submitting = ref(false);
let timer = null;

async function sendCode() {
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    showToast('请先输入正确的手机号');
    return;
  }
  try {
    await authApi.sendSms(form.phone);
    showToast('验证码已发送');
    counting.value = 60;
    timer = setInterval(() => {
      counting.value -= 1;
      if (counting.value <= 0) {
        clearInterval(timer);
        timer = null;
      }
    }, 1000);
  } catch (e) {
    // 错误已由请求层提示
  }
}

async function onSubmit() {
  submitting.value = true;
  try {
    if (mode.value === 'login') {
      await user.login({ phone: form.phone, code: form.code });
    } else {
      await user.register({
        phone: form.phone,
        code: form.code,
        inviteCode: form.inviteCode || undefined,
        nickname: form.nickname || undefined
      });
    }
    showToast('登录成功');
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/';
    router.replace(redirect.startsWith('/login') ? '/' : redirect);
  } catch (e) {
    // 错误已提示
  } finally {
    submitting.value = false;
  }
}

onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  padding: 56px 20px 32px;
  background: linear-gradient(180deg, #eef4ff 0%, #f6f7f9 42%);
}

.brand {
  text-align: center;
  margin-bottom: 22px;
}

.logo {
  width: 64px;
  height: 64px;
  margin: 0 auto 12px;
  border-radius: 18px;
  background: var(--brand);
  color: #fff;
  font-size: 30px;
  line-height: 64px;
  box-shadow: 0 8px 20px rgba(31, 111, 235, 0.28);
}

.brand h2 {
  margin: 0 0 6px;
  font-size: 21px;
}

.form-card {
  padding: 6px 0 14px;
}

.form-group {
  margin: 12px 12px 0;
}

.submit-box {
  padding: 20px 16px 8px;
}

.agreement-tip {
  text-align: center;
  color: var(--text-sub);
  font-size: 12px;
  margin: 0;
}
</style>
