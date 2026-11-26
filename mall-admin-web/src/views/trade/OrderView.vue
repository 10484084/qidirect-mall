<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import ImageUploader from '../../components/ImageUploader.vue';
import StoreSelect from '../../components/StoreSelect.vue';
import { orderApi } from '../../api';
import { ORDER_STATUS, ORDER_STATUS_OPTIONS, SHIP_TYPE_OPTIONS, statusMeta } from '../../utils/dict';
import { fen2yuan } from '../../utils/format';

const loading = ref(false);
const rows = ref([]);
const total = ref(0);
const query = reactive({
  orderNo: '',
  storeId: null,
  status: null,
  timeRange: [],
  page: 1,
  size: 20
});

const detailVisible = ref(false);
const current = ref(null);

const shipVisible = ref(false);
const shipFormRef = ref();
const shipForm = reactive({
  shipType: 1,
  shipCompany: '',
  shipNo: '',
  shipImages: [],
  remark: ''
});

function buildParams() {
  const params = {};
  Object.entries(query).forEach(([k, v]) => {
    if (k === 'timeRange') return;
    if (v !== '' && v !== null && v !== undefined) params[k] = v;
  });
  if (query.timeRange && query.timeRange.length === 2) {
    params.startTime = query.timeRange[0];
    params.endTime = query.timeRange[1];
  }
  return params;
}

async function load() {
  loading.value = true;
  try {
    const res = await orderApi.page(buildParams());
    rows.value = (res && res.records) || [];
    total.value = (res && res.total) || 0;
  } finally {
    loading.value = false;
  }
}

function search() {
  query.page = 1;
  load();
}

function reset() {
  Object.assign(query, { orderNo: '', storeId: null, status: null, timeRange: [], page: 1 });
  load();
}

async function showDetail(row) {
  current.value = await orderApi.detail(row.id);
  detailVisible.value = true;
}

function openShip(row) {
  current.value = row;
  Object.assign(shipForm, { shipType: 1, shipCompany: '', shipNo: '', shipImages: [], remark: '' });
  shipVisible.value = true;
}

async function submitShip() {
  if (!shipFormRef.value) return;
  const valid = await shipFormRef.value.validate().catch(() => false);
  if (!valid) return;
  await orderApi.ship(current.value.id, { ...shipForm });
  ElMessage.success('发货成功');
  shipVisible.value = false;
  load();
}

async function exportExcel() {
  const params = buildParams();
  delete params.page;
  delete params.size;
  await orderApi.exportExcel(params);
  ElMessage.success('导出已开始');
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">订单发货</h2>
      <div>
        <el-button @click="exportExcel">导出 Excel</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-form class="filter-bar" inline>
        <el-form-item label="订单号">
          <el-input v-model="query.orderNo" placeholder="订单号" clearable style="width: 190px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="门店">
          <div style="width: 220px">
            <StoreSelect v-model="query.storeId" placeholder="搜索门店" />
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px">
            <el-option v-for="o in ORDER_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="下单时间">
          <el-date-picker
            v-model="query.timeRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 340px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="orderNo" label="订单号" width="190" />
        <el-table-column label="门店" min-width="150">
          <template #default="{ row }">
            <div>{{ row.storeName || '-' }}</div>
            <div class="text-muted">{{ row.storePhone }}</div>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="130">
          <template #default="{ row }">
            <div class="money">¥{{ fen2yuan(row.totalAmount) }}</div>
            <div class="text-muted">预存 ¥{{ fen2yuan(row.depositAmount) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta(ORDER_STATUS, row.status).type" size="small">
              {{ statusMeta(ORDER_STATUS, row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="收货信息" min-width="180">
          <template #default="{ row }">
            <div>{{ row.receiverName }} {{ row.receiverPhone }}</div>
            <div class="text-muted ellipsis">{{ row.receiverAddr }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="下单时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button v-if="row.status === 1" link type="success" @click="openShip(row)">发货</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          @current-change="load"
          @size-change="search"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="订单详情" width="780px" top="6vh">
      <template v-if="current">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ current.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusMeta(ORDER_STATUS, current.status).type" size="small">
              {{ statusMeta(ORDER_STATUS, current.status).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="门店">{{ current.storeName }}（{{ current.storePhone }}）</el-descriptions-item>
          <el-descriptions-item label="支付单号">{{ current.payNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="订单金额">¥{{ fen2yuan(current.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="预存抵扣 / 在线支付">
            ¥{{ fen2yuan(current.depositAmount) }} / ¥{{ fen2yuan(current.onlineAmount) }}
          </el-descriptions-item>
          <el-descriptions-item label="收货人">
            {{ current.receiverName }} {{ current.receiverPhone }}
          </el-descriptions-item>
          <el-descriptions-item label="收货地址">{{ current.receiverAddr }}</el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ current.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ current.payTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发货时间">{{ current.shipTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ current.finishTime || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="current.items || []" border size="small" class="mt16">
          <el-table-column prop="productName" label="商品" min-width="160" />
          <el-table-column prop="specName" label="规格" width="120" />
          <el-table-column label="单价" width="100">
            <template #default="{ row }">¥{{ fen2yuan(row.price) }}</template>
          </el-table-column>
          <el-table-column prop="qty" label="数量" width="70" />
          <el-table-column label="小计" width="100">
            <template #default="{ row }">¥{{ fen2yuan(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="赠品" width="70">
            <template #default="{ row }">
              <el-tag v-if="row.isGift" type="success" size="small">赠</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <template v-if="current.shipTime">
          <el-divider content-position="left">发货信息</el-divider>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="发货类型">
              {{ current.shipType === 2 ? '其他方式' : '物流发货' }}
            </el-descriptions-item>
            <el-descriptions-item label="物流公司">{{ current.shipCompany || '-' }}</el-descriptions-item>
            <el-descriptions-item label="物流单号">{{ current.shipNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注">{{ current.shipRemark || '-' }}</el-descriptions-item>
            <el-descriptions-item label="凭证" :span="2">
              <div v-if="(current.shipImages || []).length" class="image-list">
                <el-image
                  v-for="img in current.shipImages"
                  :key="img"
                  :src="img"
                  :preview-src-list="current.shipImages"
                  fit="cover"
                />
              </div>
              <span v-else class="text-muted">无</span>
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </template>
    </el-dialog>

    <el-dialog v-model="shipVisible" title="订单发货" width="520px">
      <el-form ref="shipFormRef" :model="shipForm" label-width="100px">
        <el-form-item label="发货类型">
          <el-radio-group v-model="shipForm.shipType">
            <el-radio v-for="o in SHIP_TYPE_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="shipForm.shipType === 1">
          <el-form-item label="物流公司" prop="shipCompany" :rules="[{ required: true, message: '请输入物流公司' }]">
            <el-input v-model="shipForm.shipCompany" maxlength="64" />
          </el-form-item>
          <el-form-item label="物流单号" prop="shipNo" :rules="[{ required: true, message: '请输入物流单号' }]">
            <el-input v-model="shipForm.shipNo" maxlength="64" />
          </el-form-item>
        </template>
        <el-form-item label="发货凭证">
          <ImageUploader v-model="shipForm.shipImages" :limit="5" tip="可上传发货单/签收单等图片" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="shipForm.remark" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipVisible = false">取消</el-button>
        <el-button type="primary" @click="submitShip">确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
