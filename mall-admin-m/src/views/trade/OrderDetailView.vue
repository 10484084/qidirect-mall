<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showFailToast, showImagePreview, showSuccessToast } from 'vant';
import { orderApi } from '../../api';
import { uploadFile } from '../../api/request';
import { ORDER_STATUS, SHIP_TYPE_OPTIONS } from '../../utils/dict';
import { fen2yuan } from '../../utils/format';

const route = useRoute();
const router = useRouter();

const loading = ref(true);
const detail = ref(null);
const shipVisible = ref(false);
const submitting = ref(false);
const uploading = ref(false);
const fileList = ref([]);

const shipForm = reactive({
  shipType: 1,
  shipCompany: '',
  shipNo: '',
  shipImages: [],
  remark: ''
});

const statusInfo = computed(() => ORDER_STATUS[detail.value?.status] || { label: '-', type: 'info' });

async function load() {
  loading.value = true;
  try {
    detail.value = await orderApi.detail(route.params.id);
  } finally {
    loading.value = false;
  }
}

watch(
  fileList,
  (list) => {
    shipForm.shipImages = list.map((f) => f.url).filter(Boolean);
  },
  { deep: true }
);

async function onAfterRead(item) {
  const items = Array.isArray(item) ? item : [item];
  uploading.value = true;
  try {
    for (const it of items) {
      it.status = 'uploading';
      it.message = '上传中';
      try {
        const url = await uploadFile(it.file);
        it.url = url;
        it.status = 'done';
        it.message = '';
      } catch (e) {
        it.status = 'failed';
        it.message = '上传失败';
      }
    }
  } finally {
    uploading.value = false;
  }
}

function openShip() {
  Object.assign(shipForm, { shipType: 1, shipCompany: '', shipNo: '', shipImages: [], remark: '' });
  fileList.value = [];
  shipVisible.value = true;
}

async function submitShip() {
  if (uploading.value) {
    showFailToast('图片上传中，请稍候');
    return;
  }
  if (shipForm.shipType === 1 && !shipForm.shipCompany.trim()) {
    showFailToast('请输入物流公司');
    return;
  }
  if (shipForm.shipType === 1 && !shipForm.shipNo.trim()) {
    showFailToast('请输入物流单号');
    return;
  }
  submitting.value = true;
  try {
    await orderApi.ship(detail.value.id, {
      shipType: shipForm.shipType,
      shipCompany: shipForm.shipCompany.trim(),
      shipNo: shipForm.shipNo.trim(),
      shipImages: shipForm.shipImages,
      remark: shipForm.remark.trim()
    });
    showSuccessToast('发货成功');
    shipVisible.value = false;
    await load();
  } finally {
    submitting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page-plain detail-page">
    <van-nav-bar title="订单详情" left-arrow fixed placeholder @click-left="router.back()" />

    <van-loading v-if="loading" class="loading" vertical>加载中...</van-loading>

    <template v-else-if="detail">
      <div class="status-banner" :class="`banner-${statusInfo.type}`">
        <div class="banner-label">{{ statusInfo.label }}</div>
        <div class="banner-sub">{{ detail.orderNo }}</div>
      </div>

      <div class="card">
        <div class="row">
          <span class="muted">门店</span>
          <span class="strong">{{ detail.storeName || '-' }}</span>
        </div>
        <div class="row">
          <span class="muted">门店手机号</span>
          <a :href="`tel:${detail.storePhone}`" class="link">{{ detail.storePhone }}</a>
        </div>
        <div class="row">
          <span class="muted">下单时间</span>
          <span>{{ detail.createdAt }}</span>
        </div>
        <div class="row">
          <span class="muted">支付时间</span>
          <span>{{ detail.payTime || '-' }}</span>
        </div>
        <div class="row">
          <span class="muted">支付单号</span>
          <span class="ellipsis small">{{ detail.payNo || '-' }}</span>
        </div>
      </div>

      <div class="section-title">收货信息</div>
      <div class="card">
        <div class="row">
          <span class="strong">{{ detail.receiverName }}</span>
          <a :href="`tel:${detail.receiverPhone}`" class="link">{{ detail.receiverPhone }}</a>
        </div>
        <div class="addr">{{ detail.receiverAddr }}</div>
      </div>

      <div class="section-title">商品明细</div>
      <div class="card">
        <div v-for="(item, idx) in detail.items || []" :key="idx" class="goods">
          <div class="goods-main">
            <div class="goods-name">
              {{ item.productName }}
              <span v-if="item.isGift" class="tag-text tag-success gift">赠</span>
            </div>
            <div class="muted">{{ item.specName || '默认规格' }}</div>
          </div>
          <div class="goods-right">
            <div>¥{{ fen2yuan(item.price) }}</div>
            <div class="muted">×{{ item.qty }}</div>
          </div>
        </div>
      </div>

      <div class="section-title">金额</div>
      <div class="card">
        <div class="row">
          <span class="muted">订单总额</span>
          <span class="money big">¥{{ fen2yuan(detail.totalAmount) }}</span>
        </div>
        <div class="row">
          <span class="muted">预存抵扣</span>
          <span>¥{{ fen2yuan(detail.depositAmount) }}</span>
        </div>
        <div class="row">
          <span class="muted">在线支付</span>
          <span>¥{{ fen2yuan(detail.onlineAmount) }}</span>
        </div>
      </div>

      <template v-if="detail.shipTime">
        <div class="section-title">发货信息</div>
        <div class="card">
          <div class="row">
            <span class="muted">发货方式</span>
            <span>{{ detail.shipType === 2 ? '其他方式' : '物流发货' }}</span>
          </div>
          <div class="row">
            <span class="muted">物流公司</span>
            <span>{{ detail.shipCompany || '-' }}</span>
          </div>
          <div class="row">
            <span class="muted">物流单号</span>
            <span>{{ detail.shipNo || '-' }}</span>
          </div>
          <div class="row">
            <span class="muted">发货时间</span>
            <span>{{ detail.shipTime }}</span>
          </div>
          <div v-if="detail.shipRemark" class="row">
            <span class="muted">备注</span>
            <span class="ellipsis">{{ detail.shipRemark }}</span>
          </div>
          <div v-if="(detail.shipImages || []).length" class="proofs">
            <van-image
              v-for="img in detail.shipImages"
              :key="img"
              :src="img"
              width="72"
              height="72"
              fit="cover"
              radius="8"
              @click="showImagePreview(detail.shipImages, 0)"
            />
          </div>
        </div>
      </template>

      <div v-if="detail.status === 1" class="bottom-bar">
        <van-button type="primary" block round @click="openShip">去发货</van-button>
      </div>
    </template>

    <van-popup v-model:show="shipVisible" position="bottom" round :style="{ maxHeight: '88%' }" safe-area-inset-bottom>
      <div class="sheet">
        <div class="sheet-title">订单发货</div>
        <van-cell-group inset>
          <van-field label="发货类型">
            <template #input>
              <van-radio-group v-model="shipForm.shipType" direction="horizontal">
                <van-radio v-for="o in SHIP_TYPE_OPTIONS" :key="o.value" :name="o.value">{{ o.label }}</van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <template v-if="shipForm.shipType === 1">
            <van-field v-model="shipForm.shipCompany" label="物流公司" placeholder="如 顺丰速运" maxlength="64" />
            <van-field v-model="shipForm.shipNo" label="物流单号" placeholder="请输入运单号" maxlength="64" />
          </template>
          <van-field label="发货凭证">
            <template #input>
              <van-uploader v-model="fileList" :max-count="5" accept="image/*,application/pdf" :after-read="onAfterRead" />
            </template>
          </van-field>
          <van-field
            v-model="shipForm.remark"
            label="备注"
            type="textarea"
            rows="2"
            maxlength="255"
            show-word-limit
            placeholder="选填"
          />
        </van-cell-group>

        <div class="sheet-actions">
          <van-button plain block round @click="shipVisible = false">取消</van-button>
          <van-button type="primary" block round :loading="submitting" @click="submitShip">确认发货</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.detail-page {
  padding-bottom: 92px;
}

.loading {
  padding: 60px 0;
}

.status-banner {
  margin: 12px;
  padding: 16px 18px;
  border-radius: var(--card-radius);
  background: linear-gradient(135deg, #1f6feb, #0f4fbf);
  color: #fff;
}

.banner-warning {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}

.banner-success {
  background: linear-gradient(135deg, #22c55e, #15803d);
}

.banner-info {
  background: linear-gradient(135deg, #94a3b8, #64748b);
}

.banner-label {
  font-size: 20px;
  font-weight: 600;
}

.banner-sub {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.85;
  font-variant-numeric: tabular-nums;
}

.card {
  margin: 0 12px 10px;
}

.section-title {
  margin: 14px 16px 8px;
}

.link {
  color: var(--brand);
  text-decoration: none;
}

.small {
  font-size: 12px;
  max-width: 60%;
}

.addr {
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.5;
  color: var(--text-main);
}

.goods {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid #f2f4f7;
}

.goods:last-child {
  border-bottom: none;
}

.goods-name {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 3px;
}

.gift {
  margin-left: 4px;
}

.goods-right {
  text-align: right;
  flex-shrink: 0;
  font-size: 14px;
}

.money.big {
  font-size: 17px;
}

.proofs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.96);
  border-top: 1px solid #eef0f3;
  backdrop-filter: blur(8px);
}

.sheet {
  padding: 18px 0 16px;
}

.sheet-title {
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 14px;
}

.sheet-actions {
  display: flex;
  gap: 10px;
  padding: 18px 16px 0;
}
</style>
