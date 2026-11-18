<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { parentChangeApi } from '../../api';
import { PARENT_CHANGE_STATUS, PARENT_CHANGE_STATUS_OPTIONS, statusMeta } from '../../utils/dict';

const loading = ref(false);
const rows = ref([]);
const status = ref(null);

const auditVisible = ref(false);
const current = ref(null);
const auditForm = reactive({ approved: true, remark: '' });

async function load() {
  loading.value = true;
  try {
    rows.value = (await parentChangeApi.list(status.value)) || [];
  } finally {
    loading.value = false;
  }
}

function openAudit(row) {
  current.value = row;
  auditForm.approved = true;
  auditForm.remark = '';
  auditVisible.value = true;
}

async function submitAudit() {
  await parentChangeApi.audit(current.value.id, { ...auditForm });
  ElMessage.success(auditForm.approved ? '已通过' : '已驳回');
  auditVisible.value = false;
  load();
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">改上级审批</h2>
    </div>

    <el-card shadow="never">
      <el-form class="filter-bar" inline>
        <el-form-item label="状态">
          <el-select v-model="status" clearable placeholder="全部" style="width: 130px" @change="load">
            <el-option
              v-for="o in PARENT_CHANGE_STATUS_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="load">刷新</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="storeId" label="门店ID" width="90" />
        <el-table-column prop="oldParentId" label="原上级ID" width="110" />
        <el-table-column prop="newParentId" label="新上级ID" width="110" />
        <el-table-column prop="oldPath" label="原路径" min-width="120" show-overflow-tooltip />
        <el-table-column prop="newPath" label="新路径" min-width="120" show-overflow-tooltip />
        <el-table-column prop="reason" label="申请原因" min-width="140" show-overflow-tooltip />
        <el-table-column prop="applicant" label="申请人" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta(PARENT_CHANGE_STATUS, row.status).type" size="small">
              {{ statusMeta(PARENT_CHANGE_STATUS, row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approver" label="审批人" width="100" />
        <el-table-column prop="createdAt" label="申请时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" link type="primary" @click="openAudit(row)">
              审批
            </el-button>
            <span v-else class="text-muted">已处理</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="auditVisible" title="审批改上级申请" width="460px">
      <el-form label-width="80px">
        <el-form-item label="审批结果">
          <el-radio-group v-model="auditForm.approved">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input v-model="auditForm.remark" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAudit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>
