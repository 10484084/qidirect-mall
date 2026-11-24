<template>
  <div class="page">
    <PageHeader title="实名认证" />

    <div class="page-body">
      <template v-if="done">
        <div class="card done-card">
          <van-icon name="passed" size="44" color="#07c160" />
          <div class="done-title">实名认证已完成</div>
          <div class="kv"><span class="muted">姓名</span><span>{{ profile.realName }}</span></div>
          <div class="kv"><span class="muted">身份证号</span><span>{{ profile.idCardMasked }}</span></div>
          <div class="form-btn">
            <van-button type="primary" block round @click="router.replace('/bind-bank')">
              {{ profile.bankAccountMasked ? '返回首页' : '下一步：绑定收款卡' }}
            </van-button>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="card">
          <div class="card-title">填写实名信息</div>
          <p class="muted small tip">请填写本人真实姓名与身份证号，用于加盟、下单与提现。</p>
          <van-form @submit="onSubmit">
            <van-field
              v-model="form.realName"
              label="姓名"
              maxlength="32"
              placeholder="请输入真实姓名"
              :rules="[{ required: true, message: '请输入姓名' }]"
            />
            <van-field
              v-model="form.idCard"
              label="身份证号"
              maxlength="18"
              placeholder="请输入 18 位身份证号"
              :rules="[{ required: true, message: '请输入身份证号' }]"
            />
            <div class="form-btn">
              <van-button type="primary" block round native-type="submit" :loading="submitting">
                下一步
              </van-button>
            </div>
          </van-form>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import PageHeader from '../components/PageHeader.vue';
import { storeApi } from '../api';
import { useUserStore } from '../stores/user';

const router = useRouter();
const user = useUserStore();

const profile = computed(() => user.profile || {});
const done = computed(() => Number(profile.value.authStatus) > 0);
const submitting = ref(false);
const form = reactive({ realName: '', idCard: '' });

const ID_CARD_RE = /^\d{17}[\dXx]$/;

async function onSubmit() {
  const realName = form.realName.trim();
  const idCard = form.idCard.trim().toUpperCase();
  if (!realName) {
    showToast('请输入姓名');
    return;
  }
  if (!ID_CARD_RE.test(idCard)) {
    showToast('请输入正确的 18 位身份证号');
    return;
  }
  submitting.value = true;
  try {
    await storeApi.submitAuth({ realName, idCard });
    await user.loadProfile();
    showToast('实名信息已提交');
    router.replace('/bind-bank');
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  try {
    if (!user.profile) await user.loadProfile();
    if (profile.value.realName) form.realName = profile.value.realName;
  } catch (e) {
    // 忽略
  }
});
</script>

<style scoped>
.kv {
  display: flex;
  gap: 10px;
  font-size: 13px;
  padding: 6px 0;
}

.kv span:first-child {
  width: 76px;
  flex-shrink: 0;
}

.tip {
  margin: 0 0 10px;
  line-height: 1.5;
}

.form-btn {
  padding: 20px 4px 4px;
}

.done-card {
  text-align: left;
}

.done-title {
  margin: 10px 0 12px;
  font-size: 16px;
  font-weight: 600;
}
</style>
