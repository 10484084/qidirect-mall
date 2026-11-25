<template>
  <div class="pwd-page">
    <div class="head">
      <h2>修改登录密码</h2>
      <p class="muted">当前使用的是初始密码，请设置新密码后继续使用</p>
    </div>

    <div class="card form-card">
      <van-form @submit="onSubmit">
        <van-cell-group inset class="form-group">
          <van-field
            v-model="form.oldPassword"
            type="password"
            label="原密码"
            maxlength="32"
            placeholder="请输入当前密码"
            :rules="[{ required: true, message: '请输入原密码' }]"
          />
          <van-field
            v-model="form.newPassword"
            type="password"
            label="新密码"
            maxlength="32"
            placeholder="6-32 位，不能为 admin"
            :rules="[{ required: true, message: '请输入新密码' }]"
          />
          <van-field
            v-model="form.confirmPassword"
            type="password"
            label="确认密码"
            maxlength="32"
            placeholder="请再次输入新密码"
            :rules="[{ required: true, message: '请再次输入新密码' }]"
          />
        </van-cell-group>

        <div class="submit-box">
          <van-button type="primary" block round native-type="submit" :loading="submitting">
            确认修改
          </van-button>
        </div>
      </van-form>

      <p class="tip">修改成功后需使用新密码重新登录其他设备</p>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import { useUserStore } from '../stores/user';

const router = useRouter();
const user = useUserStore();

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
});
const submitting = ref(false);

async function onSubmit() {
  const newPwd = form.newPassword.trim();
  if (newPwd.length < 6 || newPwd.length > 32) {
    showToast('新密码长度需在 6-32 位之间');
    return;
  }
  if (newPwd.toLowerCase() === 'admin') {
    showToast('新密码不能与初始密码相同');
    return;
  }
  if (newPwd !== form.confirmPassword.trim()) {
    showToast('两次输入的新密码不一致');
    return;
  }
  submitting.value = true;
  try {
    await user.changePassword({ oldPassword: form.oldPassword, newPassword: newPwd });
    showToast('密码修改成功');
    router.replace('/');
  } catch (e) {
    // 错误已由请求拦截器提示
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.pwd-page {
  min-height: 100vh;
  padding: 40px 8px 32px;
  background: linear-gradient(180deg, #eaf2ea 0%, #f6f7f9 42%);
}

.head {
  padding: 0 20px 20px;
}

.head h2 {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 600;
  color: var(--text-main);
}

.head p {
  margin: 0;
  font-size: 13px;
}

.card {
  background: #fff;
  border-radius: 14px;
  margin: 0 12px;
  padding: 8px 0 20px;
  box-shadow: 0 2px 10px rgba(31, 111, 235, 0.06);
}

.form-group {
  margin-top: 8px;
}

.submit-box {
  padding: 18px 16px 0;
}

.tip {
  margin: 16px;
  text-align: center;
  font-size: 12px;
  color: var(--text-sub);
}
</style>
