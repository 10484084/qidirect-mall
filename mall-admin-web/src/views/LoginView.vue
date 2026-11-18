<script setup>
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useUserStore } from '../stores/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const formRef = ref();
const loading = ref(false);

const form = reactive({
  account: '',
  password: ''
});

const rules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
};

async function onSubmit() {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  loading.value = true;
  try {
    await userStore.login(form.account, form.password);
    ElMessage.success('登录成功');
    const redirect = route.query.redirect;
    router.replace(typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/dashboard');
  } catch (e) {
    /* 错误提示由请求拦截器统一处理 */
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="login-title">直销商城 · 厂家后台</div>
      <div class="login-sub text-muted">管理门店、商品、订单、提现与财务</div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="onSubmit">
        <el-form-item label="账号" prop="account">
          <el-input v-model="form.account" placeholder="请输入管理员账号" clearable />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="loading" @click="onSubmit">登 录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2d3d 0%, #3a4b5c 100%);
}

.login-card {
  width: 380px;
  padding: 8px 12px 4px;
}

.login-title {
  font-size: 20px;
  font-weight: 600;
  text-align: center;
}

.login-sub {
  text-align: center;
  font-size: 12px;
  margin: 6px 0 20px;
}

.login-btn {
  width: 100%;
}
</style>
