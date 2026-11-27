<script setup>
import { computed, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant';
import { useUserStore } from '../stores/user';

const router = useRouter();
const userStore = useUserStore();

const phoneVisible = ref(false);
const phoneSaving = ref(false);
const counting = ref(0);
const phoneForm = reactive({ code: '', newPhone: '' });
let phoneTimer = null;

const maskedPhone = computed(() => {
  const p = userStore.phone || '';
  return p.length === 11 ? p.slice(0, 3) + '****' + p.slice(7) : '未绑定';
});

function openPhone() {
  phoneForm.code = '';
  phoneForm.newPhone = '';
  phoneVisible.value = true;
}

async function sendPhoneCode() {
  await authApi.sendChangePhoneCode();
  showSuccessToast('验证码已发送到原手机号');
  counting.value = 60;
  clearInterval(phoneTimer);
  phoneTimer = setInterval(() => {
    counting.value -= 1;
    if (counting.value <= 0) clearInterval(phoneTimer);
  }, 1000);
}

async function doChangePhone() {
  if (!phoneForm.code || !phoneForm.newPhone) {
    showFailToast('请填写验证码与新手机号');
    return;
  }
  phoneSaving.value = true;
  try {
    await authApi.changePhone({ code: phoneForm.code, newPhone: phoneForm.newPhone });
    showSuccessToast('手机号已修改');
    phoneVisible.value = false;
    userStore.setPhone(phoneForm.newPhone);
  } finally {
    phoneSaving.value = false;
  }
}

const initial = computed(() => (userStore.realName || '管').slice(0, 1).toUpperCase());
const isSuper = computed(() => userStore.perms.includes('*'));

async function logout() {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确定退出当前账号吗？' });
  } catch (e) {
    return;
  }
  userStore.logout();
  router.replace('/login');
}

async function clearCache() {
  try {
    await showConfirmDialog({ title: '清除缓存', message: '将清除本地缓存数据（不影响登录状态），确定继续？' });
  } catch (e) {
    return;
  }
  const keep = {
    token: localStorage.getItem('mall_admin_m_token'),
    profile: localStorage.getItem('mall_admin_m_profile'),
    account: localStorage.getItem('mall_admin_m_account')
  };
  localStorage.clear();
  if (keep.token) localStorage.setItem('mall_admin_m_token', keep.token);
  if (keep.profile) localStorage.setItem('mall_admin_m_profile', keep.profile);
  if (keep.account) localStorage.setItem('mall_admin_m_account', keep.account);
  showSuccessToast('缓存已清除');
}
</script>

<template>
  <div class="page page-plain">
    <div class="head">
      <div class="avatar">{{ initial }}</div>
      <div class="info">
        <div class="name">{{ userStore.realName || '管理员' }}</div>
        <div class="account">账号：{{ userStore.account || '-' }}</div>
      </div>
    </div>

    <div class="card stats">
      <div class="stat">
        <div class="stat-value">{{ isSuper ? '全部' : userStore.perms.length }}</div>
        <div class="stat-label">权限项</div>
      </div>
      <div class="stat">
        <div class="stat-value">v1.0</div>
        <div class="stat-label">版本</div>
      </div>
    </div>

    <van-cell-group inset class="group">
      <van-cell title="提现审核" is-link to="/withdraws" />
      <van-cell title="邀请门店" is-link to="/invite" />
      <van-cell title="修改手机号" :value="userStore.phone || '未绑定'" is-link @click="openPhone" />
    </van-cell-group>


    <van-cell-group inset class="group">
      <van-cell title="清除本地缓存" is-link @click="clearCache" />
    </van-cell-group>

    <van-cell-group inset class="group">
      <van-cell title="退出登录" is-link class="danger-cell" @click="logout" />
    </van-cell-group>

    <van-popup v-model:show="phoneVisible" position="bottom" round>
      <div class="phone-box">
        <div class="phone-title">修改手机号</div>
        <div class="phone-desc">验证码将发送到当前绑定手机号 {{ maskedPhone }}</div>
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

    <p class="tip">厂家后台 · 数据 / 商品 / 订单 / 门店 / 提现</p>
  </div>
</template>

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


.head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 26px 18px 22px;
}

.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand), var(--brand-dark));
  color: #fff;
  font-size: 24px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 16px rgba(31, 111, 235, 0.25);
}

.name {
  font-size: 19px;
  font-weight: 600;
}

.account {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-sub);
}

.stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  margin: 0 12px 8px;
  text-align: center;
}

.stat-value {
  font-size: 16px;
  font-weight: 600;
}

.stat-label {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-sub);
}

.group {
  margin: 12px 8px 0;
}

.danger-cell :deep(.van-cell__title) {
  color: #d93025;
}

.tip {
  margin: 24px 0 0;
  text-align: center;
  font-size: 12px;
  color: #b7bcc4;
}
</style>
