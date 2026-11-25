<script setup>
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showFailToast, showSuccessToast } from 'vant';
import { useUserStore } from '../stores/user';
import logoUrl from '../assets/logo.png';
import { getLastAccount } from '../utils/token';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const loading = ref(false);
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
      <span @click="toDesktop">使用电脑版后台</span>
    </div>
  </div>
</template>

<style scoped>
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
