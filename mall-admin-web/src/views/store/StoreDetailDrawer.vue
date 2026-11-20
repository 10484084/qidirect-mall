<script setup>
import { computed, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import ImageUploader from '../../components/ImageUploader.vue';
import StoreSelect from '../../components/StoreSelect.vue';
import { storeApi } from '../../api';
import { AUTH_STATUS, PARENT_CHANGE_STATUS, STORE_STATUS, statusMeta } from '../../utils/dict';
import { fen2yuan } from '../../utils/format';

const visible = ref(false);
const loading = ref(false);
const detail = ref(null);
const storeId = ref(null);
const events = ref([]);
const eventPage = ref({ page: 1, size: 20, total: 0 });
const eventType = ref('');

const qrVisible = ref(false);
const qr = ref(null);

const bankVisible = ref(false);
const bankForm = reactive({
  bankHolder: '',
  bankName: '',
  bankAccount: '',
  proofImages: [],
  reason: ''
});
const bankFormRef = ref();

const parentVisible = ref(false);
const parentForm = reactive({ newParentId: null, reason: '' });
const parentFormRef = ref();

const phoneVisible = ref(false);
const phoneForm = reactive({ phone: '' });

const pwdVisible = ref(false);
const pwdForm = reactive({ password: '' });

const freezeVisible = ref(false);
const freezeForm = reactive({ reason: '' });

const profile = computed(() => (detail.value && detail.value.profile) || {});
const wallet = computed(() => (detail.value && detail.value.wallet) || {});
const bankApproveInfo = computed(() => {
  if (!detail.value || !detail.value.bankHolderApproveAt) return '';
  return `${detail.value.bankHolderApproveBy || '-'} / ${detail.value.bankHolderApproveAt}`;
});

async function open(id) {
  storeId.value = id;
  visible.value = true;
  loading.value = true;
  try {
    detail.value = await storeApi.detail(id);
    await loadEvents(1);
  } finally {
    loading.value = false;
  }
}

async function loadEvents(page) {
  const res = await storeApi.events(storeId.value, {
    page,
    size: eventPage.value.size,
    ...(eventType.value ? { type: eventType.value } : {})
  });
  events.value = (res && res.records) || [];
  eventPage.value = { page, size: eventPage.value.size, total: (res && res.total) || 0 };
}

async function showQr() {
  qr.value = await storeApi.inviteQr(storeId.value);
  qrVisible.value = true;
}

function openBank() {
  bankForm.bankHolder = profile.value.bankHolder || '';
  bankForm.bankName = profile.value.bankName || '';
  bankForm.bankAccount = '';
  bankForm.proofImages = [];
  bankForm.reason = '';
  bankVisible.value = true;
}

async function submitBank() {
  if (!bankFormRef.value) return;
  const valid = await bankFormRef.value.validate().catch(() => false);
  if (!valid) return;
  await storeApi.overrideBank(storeId.value, { ...bankForm });
  ElMessage.success('收款卡已特批修改');
  bankVisible.value = false;
  detail.value = await storeApi.detail(storeId.value);
}

function openParent() {
  parentForm.newParentId = null;
  parentForm.reason = '';
  parentVisible.value = true;
}

async function submitParent() {
  if (!parentFormRef.value) return;
  const valid = await parentFormRef.value.validate().catch(() => false);
  if (!valid) return;
  await storeApi.changeParent(storeId.value, { ...parentForm });
  ElMessage.success('上级已调整');
  parentVisible.value = false;
  detail.value = await storeApi.detail(storeId.value);
  loadEvents(1);
}

async function submitPhone() {
  if (!/^1[3-9]\d{9}$/.test(phoneForm.phone)) {
    ElMessage.warning('请输入正确的手机号');
    return;
  }
  await storeApi.resetPhone(storeId.value, phoneForm.phone);
  ElMessage.success('手机号已重置');
  phoneVisible.value = false;
  detail.value = await storeApi.detail(storeId.value);
}

async function submitPwd() {
  if (!/^.{6,32}$/.test(pwdForm.password)) {
    ElMessage.warning('密码长度需在 6-32 位之间');
    return;
  }
  await storeApi.resetPassword(storeId.value, pwdForm.password);
  ElMessage.success('登录密码已设置');
  pwdVisible.value = false;
  pwdForm.password = '';
}

async function toggleFreeze() {
  const freezing = profile.value.status === 1;
  if (freezing) {
    freezeForm.reason = '';
    freezeVisible.value = true;
    return;
  }
  await storeApi.unfreeze(storeId.value, { reason: '后台解冻' });
  ElMessage.success('已解冻');
  detail.value = await storeApi.detail(storeId.value);
}

async function submitFreeze() {
  await storeApi.freeze(storeId.value, { reason: freezeForm.reason });
  ElMessage.success('门店已冻结');
  freezeVisible.value = false;
  detail.value = await storeApi.detail(storeId.value);
}

defineExpose({ open });
</script>

<template>
  <el-drawer v-model="visible" title="门店详情" size="62%">
    <div v-loading="loading">
      <template v-if="detail">
        <div class="actions">
          <el-button type="primary" plain @click="showQr">邀请二维码</el-button>
          <el-button @click="phoneVisible = true">重置手机号</el-button>
          <el-button @click="pwdVisible = true">设置登录密码</el-button>
          <el-button @click="openBank">收款卡特批</el-button>
          <el-button @click="openParent">改上级</el-button>
          <el-button :type="profile.status === 1 ? 'danger' : 'success'" plain @click="toggleFreeze">
            {{ profile.status === 1 ? '冻结门店' : '解冻门店' }}
          </el-button>
        </div>

        <el-descriptions title="基本信息" :column="3" border class="mt16">
          <el-descriptions-item label="门店ID">{{ profile.id }}</el-descriptions-item>
          <el-descriptions-item label="门店名称">{{ profile.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ profile.nickname || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ profile.phone }}</el-descriptions-item>
          <el-descriptions-item label="上级门店">
            {{ profile.parentName || '-' }}（{{ profile.parentId || '无' }}）
          </el-descriptions-item>
          <el-descriptions-item label="档位">
            {{ profile.levelName || '未加盟' }}
            <span v-if="profile.levelPaidAmount" class="text-muted">
              / 实付 ¥{{ fen2yuan(profile.levelPaidAmount) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusMeta(STORE_STATUS, profile.status).type" size="small">
              {{ statusMeta(STORE_STATUS, profile.status).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="实名状态">
            <el-tag :type="statusMeta(AUTH_STATUS, profile.authStatus).type" size="small">
              {{ statusMeta(AUTH_STATUS, profile.authStatus).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="邀请码">{{ profile.inviteCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="加入时间">{{ profile.joinTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="升级截止">{{ profile.upgradeDeadline || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-descriptions title="实名信息" :column="2" border class="mt16">
          <el-descriptions-item label="真实姓名">{{ profile.realName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="身份证号">{{ profile.idCardMasked || '-' }}</el-descriptions-item>
          <el-descriptions-item label="营业执照号">{{ profile.licenseNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="账号关系">
            <template v-if="profile.duplicateFlag">
              <el-tag type="danger" size="small">疑似重复</el-tag>
              <span class="text-muted">（重复于 {{ detail.duplicateOfStoreName || detail.duplicateOf || '-' }}）</span>
            </template>
            <span v-else>正常</span>
          </el-descriptions-item>
          <el-descriptions-item label="门头/资质图片" :span="2">
            <div v-if="(profile.bizImages || []).length" class="image-list">
              <el-image
                v-for="img in profile.bizImages"
                :key="img"
                :src="img"
                :preview-src-list="profile.bizImages"
                fit="cover"
              />
            </div>
            <span v-else class="text-muted">未上传</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-descriptions title="收款卡" :column="2" border class="mt16">
          <el-descriptions-item label="持卡人">{{ profile.bankHolder || '-' }}</el-descriptions-item>
          <el-descriptions-item label="开户行">{{ profile.bankName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="卡号">{{ profile.bankAccountMasked || '-' }}</el-descriptions-item>
          <el-descriptions-item label="特批记录">{{ bankApproveInfo || '无' }}</el-descriptions-item>
          <el-descriptions-item label="特批凭证" :span="2">
            <div v-if="(detail.bankHolderProof || []).length" class="image-list">
              <el-image
                v-for="img in detail.bankHolderProof"
                :key="img"
                :src="img"
                :preview-src-list="detail.bankHolderProof"
                fit="cover"
              />
            </div>
            <span v-else class="text-muted">无</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-descriptions title="钱包" :column="4" border class="mt16">
          <el-descriptions-item label="预存余额">¥{{ fen2yuan(wallet.depositBalance) }}</el-descriptions-item>
          <el-descriptions-item label="佣金余额">¥{{ fen2yuan(wallet.commissionBalance) }}</el-descriptions-item>
          <el-descriptions-item label="冻结金额">¥{{ fen2yuan(wallet.frozenAmount) }}</el-descriptions-item>
          <el-descriptions-item label="可提佣金">¥{{ fen2yuan(wallet.availableCommission) }}</el-descriptions-item>
        </el-descriptions>

        <div class="mt16">
          <div class="section-title">门店事件</div>
          <el-select
            v-model="eventType"
            clearable
            placeholder="全部类型"
            size="small"
            style="width: 180px"
            @change="loadEvents(1)"
          >
            <el-option label="加盟" value="JOIN" />
            <el-option label="预存" value="RECHARGE" />
            <el-option label="订单" value="ORDER" />
            <el-option label="提成" value="COMMISSION" />
            <el-option label="提现" value="WITHDRAW" />
          </el-select>
          <el-timeline class="mt8">
            <el-timeline-item
              v-for="e in events"
              :key="e.id"
              :timestamp="e.createdAt"
              :type="e.direction === 1 ? 'success' : 'primary'"
            >
              <div class="event-title">
                {{ e.title }}
                <el-tag v-if="e.amount" size="small" :type="e.direction === 1 ? 'success' : 'danger'">
                  {{ e.direction === 1 ? '+' : '-' }}¥{{ fen2yuan(e.amount) }}
                </el-tag>
                <span class="text-muted event-meta">{{ e.category }} {{ e.bizNo || '' }}</span>
              </div>
              <div v-if="e.detail" class="text-muted pre-wrap">{{ e.detail }}</div>
            </el-timeline-item>
            <el-empty v-if="!events.length" description="暂无事件" :image-size="60" />
          </el-timeline>
          <div class="pager">
            <el-pagination
              layout="total, prev, pager, next"
              :total="eventPage.total"
              :page-size="eventPage.size"
              :current-page="eventPage.page"
              @current-change="loadEvents"
            />
          </div>
        </div>

        <div v-if="detail.remark" class="mt16">
          <div class="section-title">备注</div>
          <div class="pre-wrap">{{ detail.remark }}</div>
        </div>
      </template>
    </div>

    <el-dialog v-model="qrVisible" title="邀请二维码" width="360px" append-to-body>
      <div v-if="qr" class="qr-box">
        <img v-if="qr.qrCode" :src="qr.qrCode" class="qr-img" alt="邀请二维码" />
        <div class="mt8">邀请码：{{ qr.inviteCode }}</div>
        <div class="text-muted pre-wrap">{{ qr.inviteUrl }}</div>
      </div>
    </el-dialog>

    <el-dialog v-model="bankVisible" title="收款卡特批修改" width="520px" append-to-body>
      <el-form ref="bankFormRef" :model="bankForm" label-width="90px">
        <el-form-item label="持卡人" prop="bankHolder" :rules="[{ required: true, message: '请输入持卡人' }]">
          <el-input v-model="bankForm.bankHolder" />
        </el-form-item>
        <el-form-item label="开户行" prop="bankName" :rules="[{ required: true, message: '请输入开户行' }]">
          <el-input v-model="bankForm.bankName" />
        </el-form-item>
        <el-form-item
          label="银行卡号"
          prop="bankAccount"
          :rules="[
            { required: true, message: '请输入银行卡号' },
            { pattern: /^\d{8,32}$/, message: '银行卡号为 8-32 位数字' }
          ]"
        >
          <el-input v-model="bankForm.bankAccount" />
        </el-form-item>
        <el-form-item label="特批凭证">
          <ImageUploader v-model="bankForm.proofImages" :limit="5" />
        </el-form-item>
        <el-form-item label="特批原因" prop="reason" :rules="[{ required: true, message: '请输入特批原因' }]">
          <el-input v-model="bankForm.reason" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bankVisible = false">取消</el-button>
        <el-button type="primary" @click="submitBank">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="parentVisible" title="调整上级门店" width="480px" append-to-body>
      <el-form ref="parentFormRef" :model="parentForm" label-width="90px">
        <el-form-item
          label="新上级"
          prop="newParentId"
          :rules="[{ required: true, message: '请选择新上级门店' }]"
        >
          <StoreSelect v-model="parentForm.newParentId" />
        </el-form-item>
        <el-form-item label="调整原因">
          <el-input v-model="parentForm.reason" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="parentVisible = false">取消</el-button>
        <el-button type="primary" @click="submitParent">确认调整</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="phoneVisible" title="重置手机号" width="400px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="新手机号">
          <el-input v-model="phoneForm.phone" maxlength="11" placeholder="请输入新手机号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="phoneVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPhone">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pwdVisible" title="设置登录密码" width="400px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="门店端使用「用户名 + 密码」登录，用户名默认为手机号"
        style="margin-bottom: 12px"
      />
      <el-form label-width="80px">
        <el-form-item label="登录密码">
          <el-input
            v-model="pwdForm.password"
            type="password"
            show-password
            maxlength="32"
            placeholder="6-32 位"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPwd">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="freezeVisible" title="冻结门店" width="420px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="冻结原因">
          <el-input v-model="freezeForm.reason" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="freezeVisible = false">取消</el-button>
        <el-button type="danger" @click="submitFreeze">确认冻结</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.actions .el-button {
  margin-left: 0;
}

.section-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.event-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.event-meta {
  font-size: 12px;
}

.qr-box {
  text-align: center;
}

.qr-img {
  width: 220px;
  height: 220px;
  object-fit: contain;
}
</style>
