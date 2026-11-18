<script setup>
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import ImageUploader from '../../components/ImageUploader.vue';
import StoreSelect from '../../components/StoreSelect.vue';
import { walletApi } from '../../api';
import { WALLET_ACCOUNT, WALLET_ACCOUNT_OPTIONS } from '../../utils/dict';
import { fen2yuan, yuan2fen } from '../../utils/format';

const formRef = ref();
const submitting = ref(false);
const form = reactive({
  storeId: null,
  account: 1,
  direction: 1,
  amountYuan: 0,
  proofImages: [],
  remark: ''
});

async function submit() {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  const amountFen = yuan2fen(form.amountYuan);
  if (!amountFen) {
    ElMessage.warning('调整金额不能为 0');
    return;
  }
  submitting.value = true;
  try {
    await walletApi.adjust({
      storeId: form.storeId,
      account: form.account,
      delta: form.direction === 1 ? amountFen : -amountFen,
      proofImages: form.proofImages,
      remark: form.remark
    });
    ElMessage.success('余额已调整');
    form.amountYuan = 0;
    form.proofImages = [];
    form.remark = '';
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">余额调整</h2>
    </div>

    <el-card shadow="never">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="余额调整会直接变更门店钱包并写入流水，必须填写原因并留存凭证，操作会记录到操作日志。"
        class="mb16"
      />
      <el-form ref="formRef" :model="form" label-width="110px" style="max-width: 640px">
        <el-form-item label="门店" prop="storeId" :rules="[{ required: true, message: '请选择门店' }]">
          <div style="width: 320px">
            <StoreSelect v-model="form.storeId" />
          </div>
        </el-form-item>
        <el-form-item label="账户" prop="account">
          <el-radio-group v-model="form.account">
            <el-radio v-for="o in WALLET_ACCOUNT_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="调整方向" prop="direction">
          <el-radio-group v-model="form.direction">
            <el-radio :value="1">增加</el-radio>
            <el-radio :value="-1">扣减</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          label="金额(元)"
          prop="amountYuan"
          :rules="[{ required: true, message: '请输入调整金额' }]"
        >
          <el-input-number v-model="form.amountYuan" :min="0" :precision="2" :step="100" style="width: 200px" />
          <span class="text-muted ml8">
            实际调整：{{ form.direction === 1 ? '+' : '-' }}¥{{ fen2yuan(yuan2fen(form.amountYuan)) }}（{{
              WALLET_ACCOUNT[form.account]
            }}）
          </span>
        </el-form-item>
        <el-form-item label="凭证">
          <ImageUploader v-model="form.proofImages" :limit="5" />
        </el-form-item>
        <el-form-item
          label="调整原因"
          prop="remark"
          :rules="[{ required: true, message: '请填写调整原因' }]"
        >
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">确认调整</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.mb16 {
  margin-bottom: 16px;
}

.ml8 {
  margin-left: 8px;
  font-size: 12px;
}
</style>
