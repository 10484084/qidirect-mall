<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showConfirmDialog, showToast } from 'vant';
import { categoryApi, giftRuleApi, levelApi, productApi, uploadFile } from '../../api';
import { fen2yuan, yuan2fen } from '../../utils/format';

defineOptions({ name: 'ProductEditView' });

const route = useRoute();
const router = useRouter();
const productId = route.params.id ? Number(route.params.id) : null;
const isEdit = computed(() => !!productId);

const loading = ref(false);
const saving = ref(false);
const categories = ref([]);
const levels = ref([]);

const form = reactive({
  name: '',
  categoryId: null,
  unit: '',
  recommend: 0,
  v3GiftEnabled: 0,
  description: '',
  cover: '',
  images: []
});

// 规格：第一项固定为主单位，其余为辅单位
const skus = ref([]);
const giftRules = ref([]);
const removedGiftRuleIds = ref([]);

const sortedLevels = computed(() =>
  [...levels.value].sort((a, b) => (a.fee - b.fee) || (a.sort - b.sort))
);

const categoryText = computed(() => {
  const hit = categories.value.find((c) => c.id === form.categoryId);
  return hit ? hit.name : '';
});

const showCategoryPicker = ref(false);
const categoryColumns = computed(() =>
  categories.value.map((c) => ({ text: c.name, value: c.id }))
);

function onCategoryConfirm({ selectedOptions }) {
  const opt = selectedOptions && selectedOptions[0];
  if (opt) form.categoryId = opt.value;
  showCategoryPicker.value = false;
}

function emptySku(isBase = false) {
  return { id: null, isBase, specName: '', factor: '2', prices: {} };
}

function addSku() {
  skus.value.push(emptySku(false));
}

async function removeSku(index) {
  if (skus.value[index].isBase) {
    showToast('主单位不可删除');
    return;
  }
  try {
    await showConfirmDialog({ title: '删除规格', message: `确定删除「${skus.value[index].specName || '该规格'}」吗？` });
  } catch (e) {
    return;
  }
  skus.value.splice(index, 1);
}

// 规格下拉选项（买赠用）
const specOptions = computed(() =>
  skus.value.map((s) => ({
    text: s.isBase ? (form.unit || '主单位') : (s.specName || '未命名规格'),
    value: s.isBase ? '__base__' : s.specName
  }))
);

const showPicker = reactive({ key: '', visible: false, options: [], target: null });

function openSpecPicker(target, key) {
  const options = specOptions.value.slice();
  if (!options.length) {
    showToast('请先添加规格');
    return;
  }
  showPicker.options = options;
  showPicker.target = target;
  showPicker.key = key;
  showPicker.visible = true;
}

function onSpecConfirm({ selectedOptions }) {
  const opt = selectedOptions && selectedOptions[0];
  if (opt && showPicker.target) {
    showPicker.target[showPicker.key] = opt.value;
  }
  showPicker.visible = false;
}

function addGiftRule() {
  giftRules.value.push({ id: null, thresholdQty: '1', thresholdSpec: '__base__', giftQty: '1', giftSpec: '__base__' });
}

function removeGiftRule(index) {
  const rule = giftRules.value[index];
  if (rule && rule.id) removedGiftRuleIds.value.push(rule.id);
  giftRules.value.splice(index, 1);
}

function specText(value) {
  const hit = specOptions.value.find((o) => o.value === value);
  return hit ? hit.text : '请选择';
}

// 图片上传
const coverUploading = ref(false);
const imagesUploading = ref(false);

async function pickImage(kind) {
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = 'image/jpeg,image/png,image/webp';
  input.onchange = async () => {
    const file = input.files && input.files[0];
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) {
      showToast('图片不能超过 5MB');
      return;
    }
    if (kind === 'cover') coverUploading.value = true;
    else imagesUploading.value = true;
    try {
      const url = await uploadFile(file);
      if (kind === 'cover') form.cover = url;
      else if (form.images.length < 6) form.images.push(url);
      showToast('上传成功');
    } finally {
      if (kind === 'cover') coverUploading.value = false;
      else imagesUploading.value = false;
    }
  };
  input.click();
}

function removeImage(index) {
  form.images.splice(index, 1);
}

function onPriceInput(sku, levelId, value) {
  const cleaned = String(value ?? '')
    .replace(/[^\d.]/g, '')
    .replace(/^(\d*\.\d{2}).*$/, '$1');
  sku.prices[levelId] = cleaned;
}

async function load() {
  loading.value = true;
  try {
    const [cats, lvs] = await Promise.all([categoryApi.list(), levelApi.list()]);
    categories.value = cats || [];
    levels.value = lvs || [];
    if (!isEdit.value) {
      form.categoryId = categories.value.length ? categories.value[0].id : null;
      skus.value = [emptySku(true)];
      return;
    }
    const detail = await productApi.detail(productId);
    form.name = detail.name;
    form.categoryId = detail.categoryId;
    form.unit = detail.unit || '';
    form.recommend = detail.recommend || 0;
    form.v3GiftEnabled = detail.v3GiftEnabled || 0;
    form.description = detail.description || '';
    form.cover = detail.cover || '';
    form.images = detail.images ? detail.images.slice() : [];

    const baseUnit = detail.unit || '';
    skus.value = (detail.skus || []).map((s, index) => {
      const isBase = index === 0 || s.factor === 1;
      const prices = {};
      sortedLevels.value.forEach((l) => {
        const hit = (s.prices || []).find((p) => p.levelId === l.id);
        prices[l.id] = hit ? fen2yuan(hit.price) : '';
      });
      return {
        id: s.id,
        isBase,
        specName: isBase ? baseUnit : s.specName,
        factor: String(s.factor || 1),
        prices
      };
    });
    if (!skus.value.length) skus.value = [emptySku(true)];

    // 买赠规则
    const rules = (await giftRuleApi.list(productId).catch(() => [])) || [];
    giftRules.value = rules.filter((r) => r.enabled === 1).map((r) => {
      const thresholdSku = (!r.thresholdSkuId || r.thresholdSkuId === 0)
        ? skus.value[0]
        : skus.value.find((s) => s.id === r.thresholdSkuId);
      const giftSku = skus.value.find((s) => s.id === r.giftSkuId);
      return {
        id: r.id,
        thresholdQty: String(r.thresholdQty),
        thresholdSpec: thresholdSku ? (thresholdSku.isBase ? '__base__' : thresholdSku.specName) : '__base__',
        giftQty: String(r.giftQty),
        giftSpec: giftSku ? (giftSku.isBase ? '__base__' : giftSku.specName) : '__base__'
      };
    });
  } finally {
    loading.value = false;
  }
}

function validate() {
  if (!form.name.trim()) return '请输入商品名称';
  if (!form.categoryId) return '请选择商品分组';
  if (!form.unit.trim()) return '请填写主单位，如 瓶/袋/盒';
  for (let i = 1; i < skus.value.length; i += 1) {
    const s = skus.value[i];
    if (!s.specName || !s.specName.trim()) return `第 ${i + 1} 个规格名称不能为空`;
    if (!Number(s.factor) || Number(s.factor) < 2) return `规格「${s.specName}」换算需 ≥ 2`;
  }
  if (form.v3GiftEnabled === 1) {
    if (!giftRules.value.length) return '请至少添加一条买赠规则';
    for (let i = 0; i < giftRules.value.length; i += 1) {
      const r = giftRules.value[i];
      if (!Number(r.thresholdQty) || !Number(r.giftQty)) return `第 ${i + 1} 条买赠数量需大于 0`;
    }
  }
  return '';
}

function matchSku(savedSkus, specKey) {
  if (specKey === '__base__') return savedSkus[0];
  return savedSkus.find((s) => s.specName === specKey);
}

async function submit() {
  const err = validate();
  if (err) {
    showToast(err);
    return;
  }
  const unit = form.unit.trim();
  const payload = {
    categoryId: form.categoryId,
    name: form.name.trim(),
    unit,
    cover: form.cover,
    images: form.images,
    description: form.description,
    v3GiftEnabled: form.v3GiftEnabled,
    recommend: form.recommend,
    status: 1,
    sort: 0,
    skus: skus.value.map((s, index) => ({
      id: s.id || undefined,
      specName: s.isBase ? unit : s.specName.trim(),
      factor: s.isBase ? 1 : Number(s.factor) || 1,
      sort: index + 1,
      prices: sortedLevels.value.map((l) => ({
        levelId: l.id,
        price: yuan2fen(s.prices[l.id] || 0)
      }))
    }))
  };
  saving.value = true;
  try {
    const saved = isEdit.value
      ? await productApi.update(productId, payload)
      : await productApi.create(payload);
    await saveGiftRules(saved);
    showToast(isEdit.value ? '已保存' : '已创建');
    router.replace(`/products/${saved.id}`);
  } finally {
    saving.value = false;
  }
}

async function saveGiftRules(saved) {
  const savedSkus = (saved && saved.skus) || [];
  for (const id of removedGiftRuleIds.value) {
    await giftRuleApi.remove(id).catch(() => {});
  }
  if (form.v3GiftEnabled !== 1) {
    for (const rule of giftRules.value) {
      if (rule.id) await giftRuleApi.setEnabled(rule.id, false).catch(() => {});
    }
    return;
  }
  for (const rule of giftRules.value) {
    const thresholdSku = matchSku(savedSkus, rule.thresholdSpec);
    const giftSku = matchSku(savedSkus, rule.giftSpec);
    if (!thresholdSku || !giftSku) continue;
    const body = {
      productId: saved.id,
      thresholdSkuId: thresholdSku.id,
      giftSkuId: giftSku.id,
      thresholdQty: Number(rule.thresholdQty) || 1,
      giftQty: Number(rule.giftQty) || 1,
      enabled: 1
    };
    if (rule.id) await giftRuleApi.update(rule.id, body);
    else await giftRuleApi.create(body);
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <van-nav-bar
      :title="isEdit ? '编辑商品' : '新增商品'"
      left-arrow
      @click-left="router.back()"
    />

    <van-loading v-if="loading" class="loading" size="24">加载中...</van-loading>

    <template v-else>
      <div class="card">
        <div class="section-title">基础信息</div>
        <van-field v-model="form.name" label="商品名称" placeholder="请输入商品名称" maxlength="64" />
        <van-field
          :model-value="categoryText"
          label="商品分组"
          placeholder="请选择分组"
          readonly
          is-link
          @click="showCategoryPicker = true"
        />
        <van-field v-model="form.unit" label="主单位" placeholder="如 瓶 / 袋 / 盒" maxlength="8" />
        <van-cell title="是否推荐" center>
          <template #right-icon>
            <van-switch v-model="form.recommend" :active-value="1" :inactive-value="0" size="20" />
          </template>
        </van-cell>
        <van-cell title="参与5万档买赠" center>
          <template #right-icon>
            <van-switch v-model="form.v3GiftEnabled" :active-value="1" :inactive-value="0" size="20" />
          </template>
        </van-cell>
      </div>

      <div v-if="form.v3GiftEnabled === 1" class="card">
        <div class="section-head">
          <span class="section-title">买赠规则</span>
          <van-button size="mini" plain type="primary" @click="addGiftRule">+ 新增规则</van-button>
        </div>
        <div v-for="(rule, idx) in giftRules" :key="idx" class="gift-row">
          <span class="gift-label">买</span>
          <van-field v-model="rule.thresholdQty" class="gift-qty" type="digit" placeholder="数量" />
          <span class="gift-spec" @click="openSpecPicker(rule, 'thresholdSpec')">{{ specText(rule.thresholdSpec) }}</span>
          <span class="gift-label">送</span>
          <van-field v-model="rule.giftQty" class="gift-qty" type="digit" placeholder="数量" />
          <span class="gift-spec" @click="openSpecPicker(rule, 'giftSpec')">{{ specText(rule.giftSpec) }}</span>
          <van-icon name="delete-o" class="gift-del" @click="removeGiftRule(idx)" />
        </div>
        <div v-if="!giftRules.length" class="muted small">尚未添加规则</div>
        <div class="muted small gift-tip">门槛按换算关系折算成主单位后与购买量比较。</div>
      </div>

      <div class="card">
        <div class="section-title">封面图</div>
        <div class="cover-row">
          <div class="cover-box" @click="pickImage('cover')">
            <van-image v-if="form.cover" :src="form.cover" fit="cover" width="88" height="88" radius="8" />
            <div v-else class="cover-empty">
              <van-icon name="photograph" size="22" />
              <span>上传</span>
            </div>
          </div>
          <span class="muted small">{{ coverUploading ? '上传中...' : '建议 800x800' }}</span>
        </div>
      </div>

      <div class="card">
        <div class="section-title">轮播图（最多 6 张）</div>
        <div class="images-row">
          <div v-for="(img, idx) in form.images" :key="img" class="image-box">
            <van-image :src="img" fit="cover" width="76" height="76" radius="6" />
            <van-icon name="cross" class="image-del" @click="removeImage(idx)" />
          </div>
          <div v-if="form.images.length < 6" class="image-box image-add" @click="pickImage('images')">
            <van-icon name="plus" size="20" />
          </div>
        </div>
        <div v-if="imagesUploading" class="muted small">上传中...</div>
      </div>

      <div class="card">
        <div class="section-title">商品描述</div>
        <van-field
          v-model="form.description"
          type="textarea"
          rows="3"
          maxlength="2000"
          show-word-limit
          placeholder="选填，介绍商品卖点、规格说明等"
        />
      </div>

      <div class="card">
        <div class="section-head">
          <span class="section-title">规格与价格（元）</span>
          <van-button size="mini" plain type="primary" @click="addSku">+ 添加规格</van-button>
        </div>
        <div v-for="(sku, index) in skus" :key="index" class="sku-card">
          <div class="sku-head">
            <van-tag :type="sku.isBase ? 'success' : 'default'" size="medium">
              {{ sku.isBase ? '主单位' : '辅单位' }}
            </van-tag>
            <span v-if="sku.isBase" class="sku-name">{{ form.unit || '未填写' }}</span>
            <van-field v-else v-model="sku.specName" class="sku-name-input" placeholder="如 箱 / 件" />
            <span v-if="!sku.isBase" class="factor-wrap">
              ×
              <van-field v-model="sku.factor" class="factor-input" type="digit" placeholder="2" />
              {{ form.unit || '主单位' }}
            </span>
            <van-icon
              v-if="!sku.isBase"
              name="delete-o"
              class="sku-del"
              @click="removeSku(index)"
            />
          </div>
          <div class="price-grid">
            <div v-for="l in sortedLevels" :key="l.id" class="price-cell">
              <div class="price-label">{{ l.name }}</div>
              <van-field
                :model-value="sku.prices[l.id]"
                type="number"
                placeholder="0.00"
                @update:model-value="(v) => onPriceInput(sku, l.id, v)"
              />
            </div>
          </div>
        </div>
      </div>

      <div class="footer">
        <van-button type="primary" block round :loading="saving" @click="submit">
          {{ isEdit ? '保存修改' : '创建商品' }}
        </van-button>
      </div>
    </template>

    <van-popup v-model:show="showCategoryPicker" position="bottom" round>
      <van-picker
        :columns="categoryColumns"
        @confirm="onCategoryConfirm"
        @cancel="showCategoryPicker = false"
      />
    </van-popup>

    <van-popup v-model:show="showPicker.visible" position="bottom" round>
      <van-picker
        :columns="showPicker.options"
        @confirm="onSpecConfirm"
        @cancel="showPicker.visible = false"
      />
    </van-popup>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
  padding-bottom: 90px;
}

.loading {
  padding: 40px 0;
}

.card {
  margin: 10px 12px;
  padding: 4px 0 10px;
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
}

.card > .section-title,
.card > .section-head,
.card > .cover-row,
.card > .images-row,
.card > .sku-card,
.card > .gift-row,
.card > .muted {
  margin-left: 14px;
  margin-right: 14px;
}

.section-title {
  padding: 12px 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0 8px;
}

.section-head .section-title {
  padding: 0;
}

.gift-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.gift-label {
  font-size: 13px;
  color: var(--text-sub);
}

.gift-qty {
  width: 58px;
  padding: 4px 8px;
  background: #f7f9f7;
  border-radius: 6px;
}

.gift-spec {
  flex: 1;
  min-width: 0;
  padding: 6px 8px;
  font-size: 13px;
  text-align: center;
  background: #f7f9f7;
  border-radius: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gift-del {
  color: #c8cdd4;
  font-size: 18px;
}

.gift-tip {
  margin-top: 4px;
  line-height: 1.5;
}

.cover-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 8px;
}

.cover-box {
  width: 88px;
  height: 88px;
  border: 1px dashed #d8ded9;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.cover-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-sub);
}

.images-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-bottom: 8px;
}

.image-box {
  position: relative;
  width: 76px;
  height: 76px;
  border-radius: 6px;
  overflow: hidden;
}

.image-add {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed #d8ded9;
  color: var(--text-sub);
}

.image-del {
  position: absolute;
  top: 2px;
  right: 2px;
  font-size: 14px;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  border-radius: 50%;
  padding: 2px;
}

.sku-card {
  margin-bottom: 12px;
  padding: 10px;
  background: #f8faf8;
  border-radius: 8px;
}

.sku-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.sku-name {
  font-size: 14px;
  font-weight: 600;
}

.sku-name-input {
  flex: 1;
  padding: 2px 8px;
  background: #fff;
  border-radius: 6px;
}

.factor-wrap {
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: 13px;
  color: var(--text-sub);
}

.factor-input {
  width: 42px;
  padding: 2px 6px;
  background: #fff;
  border-radius: 6px;
}

.sku-del {
  margin-left: auto;
  color: #c8cdd4;
  font-size: 18px;
}

.price-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.price-label {
  font-size: 12px;
  color: var(--text-sub);
  margin-bottom: 2px;
}

.price-cell :deep(.van-cell) {
  padding: 4px 8px;
  background: #fff;
  border-radius: 6px;
}

.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
}
</style>
