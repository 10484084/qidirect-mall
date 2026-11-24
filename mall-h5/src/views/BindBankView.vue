<template>
  <div class="page">
    <PageHeader title="绑定收款卡" />

    <div class="page-body">
      <div class="card">
        <div class="card-title">
          请绑定收款卡
          <van-tag v-if="hasBank" type="success">已绑定</van-tag>
        </div>
        <p class="muted small tip">提现款项会打入该卡，持卡人须与实名姓名一致。</p>

        <template v-if="hasBank">
          <div class="kv"><span class="muted">持卡人</span><span>{{ profile.bankHolder }}</span></div>
          <div class="kv"><span class="muted">开户行</span><span>{{ profile.bankName }}</span></div>
          <div class="kv"><span class="muted">卡号</span><span>{{ profile.bankAccountMasked }}</span></div>
        </template>

        <van-form @submit="onSubmit">
          <van-field
            v-model="form.bankHolder"
            label="持卡人"
            maxlength="32"
            placeholder="须与实名姓名一致"
            :rules="[{ required: true, message: '请输入持卡人' }]"
          />
          <BankSelect
            v-model="form.bankName"
            name="bankName"
            label="开户行"
            placeholder="请选择开户行"
            :rules="[{ required: true, message: '请选择开户行' }]"
          />
          <van-field
            v-model="form.bankAccount"
            label="银行卡号"
            type="digit"
            maxlength="32"
            placeholder="请输入银行卡号"
            :rules="[{ required: true, message: '请输入银行卡号' }]"
          />
          <div class="form-btn">
            <van-button type="primary" block round native-type="submit" :loading="submitting">
              {{ hasBank ? '更换收款卡' : '完成绑定' }}
            </van-button>
          </div>
          <div v-if="hasBank" class="back-btn">
            <van-button plain block round @click="router.replace('/')">返回首页</van-button>
          </div>
        </van-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import PageHeader from '../components/PageHeader.vue';
import BankSelect from '../components/BankSelect.vue';
import { storeApi } from '../api';
import { useUserStore } from '../stores/user';

const router = useRouter();
const user = useUserStore();

const profile = computed(() => user.profile || {});
const hasBank = computed(() => Boolean(profile.value.bankAccountMasked));
const submitting = ref(false);
const form = reactive({ bankHolder: '', bankName: '', bankAccount: '' });

async function onSubmit() {
  const bankHolder = form.bankHolder.trim();
  const bankName = form.bankName.trim();
  const bankAccount = form.bankAccount.replace(/\s/g, '');
  if (bankAccount.length < 12) {
    showToast('请输入正确的银行卡号');
    return;
  }
  submitting.value = true;
  try {
    await storeApi.bindBank({ bankHolder, bankName, bankAccount });
    await user.loadProfile();
    form.bankAccount = '';
    showToast(hasBank.value ? '收款卡已更新' : '绑定成功');
    router.replace('/');
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  try {
    if (!user.profile) await user.loadProfile();
    form.bankHolder = profile.value.bankHolder || profile.value.realName || '';
    form.bankName = profile.value.bankName || '';
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

.back-btn {
  padding: 10px 4px 4px;
}
</style>
