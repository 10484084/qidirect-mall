<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { levelApi } from '../../api';
import { fen2yuan, yuan2fen } from '../../utils/format';

const levels = ref([]);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    levels.value = (await levelApi.list()) || [];
  } finally {
    loading.value = false;
  }
}

const sorted = computed(() => [...levels.value].sort((a, b) => (a.fee - b.fee) || (a.sort - b.sort)));
const maxSort = computed(() => (sorted.value.length ? sorted.value[sorted.value.length - 1].sort : 0));

const dialogVisible = ref(false);
const editing = ref(null);
const formRef = ref();
const form = reactive({
  name: '',
  feeYuan: 0,
  sort: 1,
  upgradeDays: 60,
  longTerm: 0,
  hasBonus: 0,
  hasGift: 0
});

const isMaxLevel = computed(() => form.sort === maxSort.value && maxSort.value > 0);

function openCreate() {
  editing.value = null;
  Object.assign(form, { name: '', feeYuan: 0, sort: maxSort.value + 1, upgradeDays: 60, longTerm: 0, hasBonus: 0, hasGift: 0 });
  dialogVisible.value = true;
}

function openEdit(row) {
  editing.value = row;
  Object.assign(form, {
    name: row.name,
    feeYuan: Number(fen2yuan(row.fee)),
    sort: row.sort,
    upgradeDays: row.upgradeDays || 60,
    longTerm: row.longTerm || 0,
    hasBonus: row.hasBonus || 0,
    hasGift: row.hasGift || 0
  });
  dialogVisible.value = true;
}

async function submit() {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  const payload = {
    name: form.name,
    fee: yuan2fen(form.feeYuan),
    sort: Number(form.sort),
    upgradeDays: Number(form.upgradeDays) || 0,
    longTerm: isMaxLevel.value ? form.longTerm : 0,
    hasBonus: isMaxLevel.value ? form.hasBonus : 0,
    hasGift: isMaxLevel.value ? form.hasGift : 0
  };
  if (editing.value) {
    await levelApi.update(editing.value.id, payload);
  } else {
    await levelApi.create(payload);
  }
  ElMessage.success('已保存');
  dialogVisible.value = false;
  await load();
}

async function toggleStatus(row) {
  const next = row.status === 1 ? 0 : 1;
  await levelApi.changeStatus(row.id, next);
  ElMessage.success(next === 1 ? '已启用' : '已冻结，前台不再展示该档位');
  load();
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除档位「${row.name}」吗？已有门店处于该档位或已产生储值订单时会被拒绝，请改用冻结。`,
      '删除档位',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    );
  } catch (e) {
    return;
  }
  await levelApi.remove(row.id);
  ElMessage.success('已删除');
  load();
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">储值档位</h2>
      <div>
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="openCreate">新增档位</el-button>
      </div>
    </div>

    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="长期提成与团队奖励仅最高档位可开启；买赠权益各档位均可开启，具体规则在商品档案里按档位配置。"
    />

    <el-card shadow="never">
      <el-table v-loading="loading" :data="sorted" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="档位名称" min-width="120" />
        <el-table-column label="档位金额" width="130">
          <template #default="{ row }">¥{{ fen2yuan(row.fee) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '冻结' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="upgradeDays" label="升级期(天)" width="110" />
        <el-table-column label="长期提成" width="100">
          <template #default="{ row }">
            <el-tag :type="row.longTerm ? 'success' : 'info'" size="small">
              {{ row.longTerm ? '开启' : '关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="团队奖励" width="100">
          <template #default="{ row }">
            <el-tag :type="row.hasBonus ? 'success' : 'info'" size="small">
              {{ row.hasBonus ? '开启' : '关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="买赠权益" width="100">
          <template #default="{ row }">
            <el-tag :type="row.hasGift ? 'success' : 'info'" size="small">
              {{ row.hasGift ? '开启' : '关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="toggleStatus(row)">
              {{ row.status === 1 ? '冻结' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑档位' : '新增档位'" width="520px">
      <el-form ref="formRef" :model="form" label-width="110px">
        <el-form-item label="档位名称" prop="name" :rules="[{ required: true, message: '请输入名称' }]">
          <el-input v-model="form.name" maxlength="32" />
        </el-form-item>
        <el-form-item
          label="档位金额(元)"
          prop="feeYuan"
          :rules="[{ required: true, message: '请输入档位金额' }]"
        >
          <el-input-number v-model="form.feeYuan" :min="0" :precision="2" :step="1000" style="width: 200px" />
        </el-form-item>
        <el-form-item label="排序" prop="sort" :rules="[{ required: true, message: '请输入排序' }]">
          <el-input-number v-model="form.sort" :min="1" style="width: 200px" />
          <span class="text-muted ml8">数值越大档位越高</span>
        </el-form-item>
        <el-form-item label="升级期(天)">
          <el-input-number v-model="form.upgradeDays" :min="0" style="width: 200px" />
        </el-form-item>
        <el-divider content-position="left">专属权益</el-divider>
        <el-alert
          v-if="!isMaxLevel"
          type="warning"
          :closable="false"
          show-icon
          title="当前排序不是最高档位，保存时权益开关将自动关闭。"
          class="mb12"
        />
        <el-form-item label="长期提成">
          <el-switch
            v-model="form.longTerm"
            :active-value="1"
            :inactive-value="0"
            :disabled="!isMaxLevel"
          />
        </el-form-item>
        <el-form-item label="团队奖励">
          <el-switch
            v-model="form.hasBonus"
            :active-value="1"
            :inactive-value="0"
            :disabled="!isMaxLevel"
          />
        </el-form-item>
        <el-form-item label="买赠权益">
          <el-switch v-model="form.hasGift" :active-value="1" :inactive-value="0" :disabled="!isMaxLevel" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}

.ml8 {
  margin-left: 8px;
  font-size: 12px;
}
</style>
