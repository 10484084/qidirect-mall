<template>
  <div class="login-page">
    <div class="brand">
      <img class="brand-logo" :src="logoUrl" alt="发士莲 Fadsline" />
      <p class="muted small">门店储值 · 预存订货 · 团队业绩</p>
    </div>

    <div class="card form-card">
      <van-form @submit="onSubmit">
        <van-cell-group inset class="form-group">
          <van-field
            v-model="form.username"
            label="用户名"
            maxlength="32"
            clearable
            placeholder="请输入用户名（默认为手机号）"
            :rules="[{ required: true, message: '请输入用户名' }]"
          />
          <van-field
            v-model="form.password"
            type="password"
            label="密码"
            maxlength="64"
            placeholder="请输入密码"
            :rules="[{ required: true, message: '请输入密码' }]"
          />
        </van-cell-group>

        <div class="submit-box">
          <van-button type="primary" block round native-type="submit" :loading="submitting">
            登录
          </van-button>
        </div>
      </van-form>

      <p class="forgot-tip">
        <span class="link" @click="$router.push('/forgot-password')">忘记密码？</span>
      </p>

      <p class="agreement-tip">
        登录即表示同意《用户服务协议》与《隐私政策》
      </p>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import logoUrl from '../assets/logo.png';
import { useUserStore } from '../stores/user';

const route = useRoute();
const router = useRouter();
const user = useUserStore();

const form = reactive({
  username: '',
  password: ''
});
const submitting = ref(false);

async function onSubmit() {
  submitting.value = true;
  try {
    const data = await user.login({ username: form.username, password: form.password });
    if (data && data.needChangePwd) {
      showToast('首次登录，请先修改初始密码');
      router.replace('/change-password');
      return;
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
</script>

<style scoped>
.forgot-tip {
  margin: 12px 16px 0;
  text-align: right;
  font-size: 13px;
}

.forgot-tip .link {
  color: var(--brand);
}

.login-page {
  min-height: 100vh;
  padding: 56px 20px 32px;
  background: linear-gradient(180deg, #eaf2ea 0%, #f6f7f9 42%);
}

.brand {
  text-align: center;
  margin-bottom: 22px;
}

.brand-logo {
  display: block;
  width: 200px;
  height: auto;
  margin: 0 auto 10px;
}

.brand h2 {
  margin: 0 0 6px;
  font-size: 21px;
}

.form-card {
  padding: 20px 0 14px;
}

.form-group {
  margin: 0 12px;
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
