<script setup>
import { computed, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { authApi } from '../api';
import { useUserStore } from '../stores/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const formRef = ref();
const loading = ref(false);
const step = ref('account');
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
    ElMessage.warning('请输入正确的手机号');
    return;
  }
  await authApi.sendAdminResetCode({ phone: resetForm.phone });
  ElMessage.success('若该手机号已绑定管理员，验证码已发送');
  counting.value = 60;
  clearInterval(timer);
  timer = setInterval(() => {
    counting.value -= 1;
    if (counting.value <= 0) clearInterval(timer);
  }, 1000);
}

async function doReset() {
  if (!resetForm.phone || !resetForm.code || !resetForm.newPassword) {
    ElMessage.warning('请填写完整');
    return;
  }
  resetting.value = true;
  try {
    await authApi.resetAdminPassword({
      phone: resetForm.phone,
      code: resetForm.code,
      newPassword: resetForm.newPassword
    });
    ElMessage.success('密码已重置，请用新密码登录');
    resetVisible.value = false;
    step.value = 'password';
  } finally {
    resetting.value = false;
  }
}

const form = reactive({
  account: '',
  password: ''
});

const rules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
};

const maskedAccount = computed(() => form.account.trim());

async function nextStep() {
  if (!formRef.value) return;
  const valid = await formRef.value.validateField('account').catch(() => false);
  if (valid === false) return;
  if (!form.account.trim()) {
    ElMessage.warning('请输入账号');
    return;
  }
  step.value = 'password';
}

function backStep() {
  form.password = '';
  step.value = 'account';
}

async function onSubmit() {
  if (step.value === 'account') {
    await nextStep();
    return;
  }
  if (!formRef.value) return;
  const valid = await formRef.value.validateField('password').catch(() => false);
  if (valid === false) return;
  loading.value = true;
  try {
    await userStore.login(form.account.trim(), form.password);
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
          <el-input
            v-model="form.account"
            placeholder="请输入管理员账号"
            clearable
            :disabled="step === 'password'"
          />
        </el-form-item>
        <el-form-item v-if="step === 'password'" label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            autofocus
          />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="loading" @click="onSubmit">
          {{ step === 'account' ? '下一步' : '登 录' }}
        </el-button>
        <div v-if="step === 'password'" class="login-foot">
          <span class="text-muted small">当前账号：{{ maskedAccount }}</span>
          <el-link type="primary" :underline="false" @click="backStep">修改</el-link>
        </div>
        <div v-if="step === 'password'" class="login-foot">
          <span class="text-muted small">忘记密码？</span>
          <el-link type="primary" :underline="false" @click="openReset">用绑定手机号重置</el-link>
        </div>
      </el-form>
    </el-card>

    <el-dialog v-model="resetVisible" title="重置密码" width="420px">
      <el-form label-width="80px">
        <el-form-item label="手机号" required>
          <el-input v-model="resetForm.phone" maxlength="11" placeholder="账号绑定的手机号" />
        </el-form-item>
        <el-form-item label="验证码" required>
          <div class="code-row">
            <el-input v-model="resetForm.code" maxlength="6" placeholder="6 位验证码" />
            <el-button :disabled="counting > 0" @click="sendCode">
              {{ counting > 0 ? counting + 's' : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="resetForm.newPassword" type="password" show-password maxlength="32"
                    placeholder="6-32 位" />
        </el-form-item>
      </el-form>
      <div class="reset-tip text-muted small">
        验证码会发送到该手机号绑定的管理员账号；未绑定手机号请联系超级管理员重置。
      </div>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetting" @click="doReset">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.code-row .el-input {
  flex: 1;
}

.reset-tip {
  line-height: 1.6;
  padding-left: 80px;
}

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

.login-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12px;
}

.small {
  font-size: 12px;
}
</style>
