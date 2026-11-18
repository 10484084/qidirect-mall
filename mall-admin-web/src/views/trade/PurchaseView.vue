<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import ImageUploader from '../../components/ImageUploader.vue';
import StoreSelect from '../../components/StoreSelect.vue';
import { levelApi, purchaseApi, storeApi } from '../../api';
import { OFFLINE_METHOD, OFFLINE_METHOD_OPTIONS } from '../../utils/dict';
import { fen2yuan } from '../../utils/format';

const levels = ref([]);
const tab = ref('proxy');

const storeInfo = ref(null);
const loadingStore = ref(false);

const proxyFormRef = ref();
const proxyForm = reactive({
  storeId: null,
  levelId: null,
  offlineMethod: 1,
  proofImages: [],
  idImages: [],
  agreementImages: [],
  receivedAt: '',
  remark: ''
});

const collectFormRef = ref();
const collectForm = reactive({
  storeId: null,
  levelId: null,
  offlineMethod: 1,
  receivedAt: '',
  proofImages: [],
  remark: ''
});

const result = ref(null);
const submitting = ref(false);

async function onStoreChange(store) {
  result.value = null;
  if (!store) {
    storeInfo.value = null;
    return;
  }
  loadingStore.value = true;
  try {
    const detail = await storeApi.detail(store.id);
    storeInfo.value = detail;
    if (tab.value === 'collect' && detail.profile && detail.profile.levelId) {
      collectForm.levelId = detail.profile.levelId;
    }
  } finally {
    loadingStore.value = false;
  }
}

function currentLevelFee(levelId) {
  const l = levels.value.find((x) => x.id === levelId);
  return l ? l.fee : 0;
}

async function submitProxy() {
  if (!proxyFormRef.value) return;
  const valid = await proxyFormRef.value.validate().catch(() => false);
  if (!valid) return;
  submitting.value = true;
  try {
    result.value = await purchaseApi.proxyJoin({
      ...proxyForm,
      receivedAt: proxyForm.receivedAt || null
    });
    ElMessage.success('代客加盟已下单');
  } finally {
    submitting.value = false;
  }
}

async function submitCollect() {
  if (!collectFormRef.value) return;
  const valid = await collectFormRef.value.validate().catch(() => false);
  if (!valid) return;
  submitting.value = true;
  try {
    result.value = await purchaseApi.offlineCollect({
      ...collectForm,
      receivedAt: collectForm.receivedAt || null
    });
    ElMessage.success('线下收款已登记');
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  levels.value = (await levelApi.list()) || [];
});
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">代客加盟 / 线下收款</h2>
    </div>

    <el-card shadow="never">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="未加盟门店使用「代客加盟」直接开通档位；已加盟门店的升级/续费请使用「线下收款」登记。"
        class="mb16"
      />
      <el-tabs v-model="tab">
        <el-tab-pane label="代客加盟" name="proxy">
          <el-form ref="proxyFormRef" :model="proxyForm" label-width="120px" class="form">
            <el-form-item label="门店" prop="storeId" :rules="[{ required: true, message: '请选择门店' }]">
              <div style="width: 320px">
                <StoreSelect v-model="proxyForm.storeId" @change="onStoreChange" />
              </div>
              <div v-if="storeInfo" class="store-tip">
                当前档位：{{ storeInfo.profile.levelName || '未加盟' }}
                <span v-if="storeInfo.profile.levelId" class="text-danger">
                  （已加盟门店请改用线下收款）
                </span>
              </div>
            </el-form-item>
            <el-form-item label="加盟档位" prop="levelId" :rules="[{ required: true, message: '请选择档位' }]">
              <el-select v-model="proxyForm.levelId" placeholder="请选择档位" style="width: 320px">
                <el-option
                  v-for="l in levels"
                  :key="l.id"
                  :label="`${l.name}（¥${fen2yuan(l.fee)}）`"
                  :value="l.id"
                />
              </el-select>
              <span v-if="proxyForm.levelId" class="store-tip">
                应收：¥{{ fen2yuan(currentLevelFee(proxyForm.levelId)) }}
              </span>
            </el-form-item>
            <el-form-item label="收款方式" prop="offlineMethod">
              <el-radio-group v-model="proxyForm.offlineMethod">
                <el-radio v-for="o in OFFLINE_METHOD_OPTIONS" :key="o.value" :value="o.value">
                  {{ o.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="到账时间">
              <el-date-picker
                v-model="proxyForm.receivedAt"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="默认当前时间"
                style="width: 320px"
              />
            </el-form-item>
            <el-form-item
              label="收款凭证"
              prop="proofImages"
              :rules="[{ type: 'array', required: true, message: '请上传收款凭证' }]"
            >
              <ImageUploader v-model="proxyForm.proofImages" :limit="5" />
            </el-form-item>
            <el-form-item
              label="身份证件"
              prop="idImages"
              :rules="[{ type: 'array', required: true, message: '请上传身份证件' }]"
            >
              <ImageUploader v-model="proxyForm.idImages" :limit="5" />
            </el-form-item>
            <el-form-item
              label="签约协议"
              prop="agreementImages"
              :rules="[{ type: 'array', required: true, message: '请上传签约协议' }]"
            >
              <ImageUploader v-model="proxyForm.agreementImages" :limit="5" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="proxyForm.remark" type="textarea" :rows="2" style="width: 480px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="submitting" @click="submitProxy">提交代客加盟</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="线下收款" name="collect">
          <el-form ref="collectFormRef" :model="collectForm" label-width="120px" class="form">
            <el-form-item label="门店" prop="storeId" :rules="[{ required: true, message: '请选择门店' }]">
              <div style="width: 320px">
                <StoreSelect v-model="collectForm.storeId" @change="onStoreChange" />
              </div>
              <div v-if="storeInfo" class="store-tip">
                当前档位：{{ storeInfo.profile.levelName || '未加盟' }}
              </div>
            </el-form-item>
            <el-form-item label="收款档位/金额" prop="levelId" :rules="[{ required: true, message: '请选择档位' }]">
              <el-select v-model="collectForm.levelId" placeholder="请选择档位" style="width: 320px">
                <el-option
                  v-for="l in levels"
                  :key="l.id"
                  :label="`${l.name}（¥${fen2yuan(l.fee)}）`"
                  :value="l.id"
                />
              </el-select>
              <span v-if="collectForm.levelId" class="store-tip">
                应收：¥{{ fen2yuan(currentLevelFee(collectForm.levelId)) }}
              </span>
            </el-form-item>
            <el-form-item label="收款方式" prop="offlineMethod">
              <el-radio-group v-model="collectForm.offlineMethod">
                <el-radio v-for="o in OFFLINE_METHOD_OPTIONS" :key="o.value" :value="o.value">
                  {{ o.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="到账时间">
              <el-date-picker
                v-model="collectForm.receivedAt"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="默认当前时间"
                style="width: 320px"
              />
            </el-form-item>
            <el-form-item label="收款凭证">
              <ImageUploader v-model="collectForm.proofImages" :limit="5" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="collectForm.remark" type="textarea" :rows="2" style="width: 480px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="submitting" @click="submitCollect">提交线下收款</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <el-divider />
      <div class="result-title">最近单据</div>
      <el-descriptions v-if="result" :column="2" border class="mt8">
        <el-descriptions-item label="订单号">{{ result.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="支付单号">{{ result.payNo }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">{{ result.bizType }}</el-descriptions-item>
        <el-descriptions-item label="档位">{{ result.levelName }}</el-descriptions-item>
        <el-descriptions-item label="原档位">{{ result.beforeLevelId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ fen2yuan(result.amount) }}</el-descriptions-item>
        <el-descriptions-item label="收款方式">{{ OFFLINE_METHOD[result.offlineMethod] || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ result.status === 1 ? '已收款' : '待支付' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="提交后在此显示结果" :image-size="60" />
    </el-card>
  </div>
</template>

<style scoped>
.form {
  max-width: 760px;
}

.store-tip {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.mb16 {
  margin-bottom: 16px;
}

.result-title {
  font-weight: 600;
}
</style>
