<template>
  <div class="page profile-page">
    <PageHeader title="门店资料" />

    <div class="page-body">
      <div v-if="profile" class="card">
        <div class="card-title">基本信息</div>
        <div class="kv"><span class="muted">手机号</span><span>{{ profile.phone }}</span></div>
        <div class="kv"><span class="muted">门店名称</span><span>{{ profile.name || '-' }}</span></div>
        <div class="kv"><span class="muted">当前档位</span><span>{{ profile.levelId > 0 ? profile.levelName : '未加盟' }}</span></div>
        <div class="kv"><span class="muted">上级门店</span><span>{{ profile.parentName || '-' }}</span></div>
        <div class="kv"><span class="muted">邀请码</span><span>{{ profile.inviteCode || '-' }}</span></div>
        <div class="kv"><span class="muted">加盟时间</span><span>{{ formatTime(profile.joinTime) || '-' }}</span></div>
        <div class="kv"><span class="muted">升级截止</span><span>{{ formatTime(profile.upgradeDeadline) || '-' }}</span></div>
        <div class="kv"><span class="muted">门店状态</span>
          <van-tag :type="profile.status === 1 ? 'success' : 'danger'">{{ profile.status === 1 ? '正常' : '已冻结' }}</van-tag>
        </div>
      </div>

      <div class="card">
        <div class="card-title">昵称与头像</div>
        <van-field v-model="baseForm.nickname" label="昵称" maxlength="64" placeholder="请输入昵称" />
        <van-field v-model="baseForm.avatar" label="头像链接" placeholder="选填，图片 URL" />
        <div class="form-btn">
          <van-button size="small" type="primary" round :loading="savingBase" @click="saveBase">保存</van-button>
        </div>
      </div>

      <div class="card">
        <div class="card-title">
          实名认证
          <van-tag v-if="profile && profile.authStatus === 1" type="success">已认证</van-tag>
          <van-tag v-else type="warning">未认证</van-tag>
        </div>
        <template v-if="profile && profile.authStatus === 1">
          <div class="kv"><span class="muted">姓名</span><span>{{ profile.realName }}</span></div>
          <div class="kv"><span class="muted">身份证号</span><span>{{ profile.idCardMasked }}</span></div>
        </template>
        <div v-else class="auth-entry">
          <p class="muted small">填写姓名与身份证号即可完成实名，随后绑定收款卡。</p>
          <div class="form-btn">
            <van-button size="small" type="primary" round @click="$router.push('/auth-verify')">
              去实名认证
            </van-button>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-title">
          收款卡
          <van-tag v-if="hasBank" type="success">已绑定</van-tag>
          <van-tag v-else type="warning">未绑定</van-tag>
        </div>
        <template v-if="hasBank">
          <div class="kv"><span class="muted">持卡人</span><span>{{ profile.bankHolder }}</span></div>
          <div class="kv"><span class="muted">开户行</span><span>{{ profile.bankName }}</span></div>
          <div class="kv"><span class="muted">卡号</span><span>{{ profile.bankAccountMasked }}</span></div>
        </template>
        <p v-else class="muted small">尚未绑定收款卡，提现前请先绑定。</p>

        <div class="form-btn">
          <van-button size="small" type="primary" round @click="openBank">
            {{ hasBank ? '更换银行卡' : '绑定银行卡' }}
          </van-button>
        </div>
        <p class="muted small">持卡人须与实名姓名一致，不一致请联系厂家后台特批。</p>
      </div>

    </div>

    <van-popup v-model:show="bankVisible" position="bottom" round safe-area-inset-bottom>
      <div class="bank-sheet">
        <div class="bank-sheet__title">{{ hasBank ? '更换收款卡' : '绑定收款卡' }}</div>
        <p class="muted small bank-sheet__tip">持卡人须与实名姓名一致，卡号请仔细核对。</p>
        <van-form @submit="submitBank">
          <van-field
            v-model="bankForm.bankHolder"
            label="持卡人"
            maxlength="32"
            placeholder="须与实名姓名一致"
            :rules="[{ required: true, message: '请输入持卡人' }]"
          />
          <van-field
            v-model="bankForm.bankName"
            label="开户行"
            maxlength="64"
            placeholder="如 中国工商银行桓台支行"
            :rules="[{ required: true, message: '请输入开户行' }]"
          />
          <van-field
            v-model="bankForm.bankAccount"
            label="银行卡号"
            type="digit"
            maxlength="32"
            placeholder="请输入新的银行卡号"
            :rules="[{ required: true, message: '请输入银行卡号' }]"
          />
          <div class="sheet-actions">
            <van-button plain block round @click="bankVisible = false">取消</van-button>
            <van-button type="primary" block round native-type="submit" :loading="savingBank">
              确认保存
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { showToast } from 'vant';
import PageHeader from '../components/PageHeader.vue';
import { storeApi } from '../api';
import { useUserStore } from '../stores/user';
import { formatTime } from '../utils/format';

const user = useUserStore();
const profile = computed(() => user.profile);
const hasBank = computed(() => Boolean(profile.value && profile.value.bankAccountMasked));

const baseForm = reactive({ nickname: '', avatar: '' });
const bankForm = reactive({ bankHolder: '', bankName: '', bankAccount: '' });
const savingBase = ref(false);
const savingBank = ref(false);
const bankVisible = ref(false);

function openBank() {
  bankForm.bankHolder = profile.value.bankHolder || profile.value.realName || '';
  bankForm.bankName = profile.value.bankName || '';
  bankForm.bankAccount = '';
  bankVisible.value = true;
}

onMounted(async () => {
  try {
    if (!user.profile) await user.loadProfile();
    baseForm.nickname = profile.value.nickname || '';
    baseForm.avatar = profile.value.avatar || '';
    bankForm.bankHolder = profile.value.bankHolder || profile.value.realName || '';
    bankForm.bankName = profile.value.bankName || '';
  } catch (e) {
    // 错误已提示
  }
});

async function saveBase() {
  savingBase.value = true;
  try {
    await storeApi.updateProfile({ nickname: baseForm.nickname || undefined, avatar: baseForm.avatar || undefined });
    await user.loadProfile();
    showToast('已保存');
  } finally {
    savingBase.value = false;
  }
}

async function submitBank() {
  const bankAccount = bankForm.bankAccount.replace(/\s/g, '');
  if (bankAccount.length < 12) {
    showToast('请输入正确的银行卡号');
    return;
  }
  savingBank.value = true;
  try {
    await storeApi.bindBank({
      bankHolder: bankForm.bankHolder.trim(),
      bankName: bankForm.bankName.trim(),
      bankAccount
    });
    await user.loadProfile();
    bankForm.bankAccount = '';
    bankVisible.value = false;
    showToast('收款卡已保存');
  } finally {
    savingBank.value = false;
  }
}

</script>

<style scoped>
.kv {
  display: flex;
  gap: 10px;
  font-size: 13px;
  padding: 4px 0;
}

.kv span:first-child {
  width: 76px;
  flex-shrink: 0;
}

.form-btn {
  display: flex;
  justify-content: flex-end;
  padding: 10px 4px 0;
}

.bank-sheet {
  padding: 18px 0 16px;
}

.bank-sheet__title {
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 6px;
}

.bank-sheet__tip {
  margin: 0 16px 10px;
  line-height: 1.5;
}

.sheet-actions {
  display: flex;
  gap: 10px;
  padding: 20px 16px 4px;
}
</style>
