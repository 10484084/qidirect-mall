<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import ImageUploader from '../../components/ImageUploader.vue';
import { categoryApi, giftRuleApi, levelApi, productApi } from '../../api';
import { fen2yuan, yuan2fen } from '../../utils/format';

const loading = ref(false);
const rows = ref([]);
const total = ref(0);
const categories = ref([]);
const levels = ref([]);

const query = reactive({ keyword: '', categoryId: null, status: null, page: 1, size: 20 });

// 档位价格列按档位金额升序排列，列名取档位名称
const sortedLevels = computed(() => [...levels.value].sort((a, b) => (a.fee - b.fee) || (a.sort - b.sort)));

const dialogVisible = ref(false);
const editing = ref(null);
const saving = ref(false);
const form = reactive({
  categoryId: null,
  name: '',
  unit: '',
  cover: '',
  images: [],
  description: '',
  v3GiftEnabled: 0,
  recommend: 0,
  status: 1,
  sort: 0,
  skus: []
});

const giftRule = reactive({ id: null, specName: '', thresholdQty: 1, giftQty: 1, timeRange: [] });

// 赠品规格下拉：取当前表单的规格名称（新建时还没 id，用名称映射到保存后的 sku）
const specOptions = computed(() => form.skus.map((sku) => ({
  label: sku.isBase ? (form.unit || '主单位') : (sku.specName || '未命名规格'),
  value: sku.isBase ? '__base__' : sku.specName
})));

const detailVisible = ref(false);
const detailRow = ref(null);

// 第一个规格固定为主单位（factor=1，不可删除），其余为包装单位（1 包装 = N 主单位）
function emptySku(isBase = false) {
  return {
    id: undefined,
    isBase,
    specName: '',
    factor: isBase ? 1 : 2,
    sort: form.skus.length + 1,
    priceYuan: Object.fromEntries(sortedLevels.value.map((l) => [l.id, 0]))
  };
}

function onPriceInput(row, levelId, value) {
  const cleaned = String(value ?? '')
    .replace(/[^\d.]/g, '')
    .replace(/^(\d*\.\d{2}).*$/, '$1');
  row.priceYuan[levelId] = cleaned;
}

async function load() {
  loading.value = true;
  try {
    const params = {};
    Object.entries(query).forEach(([k, v]) => {
      if (v !== '' && v !== null && v !== undefined) params[k] = v;
    });
    const res = await productApi.page(params);
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

function categoryName(id) {
  const hit = categories.value.find((c) => c.id === id);
  return hit ? hit.name : '-';
}

function priceOf(sku, levelId) {
  const p = (sku.prices || []).find((x) => x.levelId === levelId);
  return p ? fen2yuan(p.price) : '-';
}

async function openCreate() {
  editing.value = null;
  Object.assign(form, {
    categoryId: categories.value.length ? categories.value[0].id : null,
    name: '',
    unit: '',
    cover: '',
    images: [],
    description: '',
    v3GiftEnabled: 0,
    recommend: 0,
    status: 1,
    sort: 0,
    skus: []
  });
  Object.assign(giftRule, { id: null, specName: '', thresholdQty: 1, giftQty: 1, timeRange: [] });
  dialogVisible.value = true;
  await ensureLevels();
  form.skus = [emptySku(true)];
}

async function loadGiftRule(productId) {
  Object.assign(giftRule, { id: null, specName: '', thresholdQty: 1, giftQty: 1, timeRange: [] });
  try {
    const rules = (await giftRuleApi.list(productId)) || [];
    const hit = rules.find((r) => r.enabled === 1) || rules[0];
    if (hit) {
      giftRule.id = hit.id;
      giftRule.thresholdQty = hit.thresholdQty;
      giftRule.giftQty = hit.giftQty;
      giftRule.timeRange = hit.startTime && hit.endTime ? [hit.startTime, hit.endTime] : [];
      // 用 skuId 反查规格名称，保存时再映射回 id
      const detailSkus = (form.skus || []);
      const matched = detailSkus.find((sku) => sku.id === hit.giftSkuId);
      giftRule.specName = matched ? (matched.isBase ? '__base__' : matched.specName) : '';
    }
  } catch (e) {
    // 规则读取失败不影响商品编辑
  }
}

async function openEdit(row) {
  await ensureLevels();
  const detail = await productApi.detail(row.id);
  editing.value = detail;
  Object.assign(form, {
    categoryId: detail.categoryId,
    name: detail.name,
    unit: detail.unit || '',
    cover: detail.cover || '',
    images: detail.images || [],
    description: detail.description || '',
    v3GiftEnabled: detail.v3GiftEnabled || 0,
    recommend: detail.recommend || 0,
    status: detail.status ?? 1,
    sort: detail.sort || 0,
    skus: (detail.skus || []).map((s, index) => ({
      id: s.id,
      isBase: index === 0 || s.factor === 1,
      specName: s.specName,
      factor: s.factor || 1,
      sort: s.sort || 1,
      priceYuan: Object.fromEntries(
        sortedLevels.value.map((l) => {
          const p = (s.prices || []).find((x) => x.levelId === l.id);
          return [l.id, p ? Number(fen2yuan(p.price)) : 0];
        })
      )
    }))
  });
  if (!form.skus.length) {
    form.skus = [emptySku(true)];
  } else {
    // 保证第一行为主单位
    form.skus.forEach((sku, i) => { sku.isBase = i === 0; });
    form.skus[0].factor = 1;
  }
  await loadGiftRule(detail.id);
  dialogVisible.value = true;
}

function addSku() {
  form.skus.push(emptySku());
}

function removeSku(index) {
  form.skus.splice(index, 1);
}

async function submit() {
  if (!form.categoryId) {
    ElMessage.warning('请选择分类');
    return;
  }
  if (!form.name) {
    ElMessage.warning('请输入商品名称');
    return;
  }
  if (!form.skus.length) {
    ElMessage.warning('至少添加一个规格');
    return;
  }
  if (!form.unit.trim()) {
    ElMessage.warning('请先填写主单位，如 瓶/袋/盒');
    return;
  }
  for (let i = 0; i < form.skus.length; i += 1) {
    const sku = form.skus[i];
    if (sku.isBase) continue;
    if (!sku.specName || !sku.specName.trim()) {
      ElMessage.warning(`第 ${i} 个包装单位名称不能为空`);
      return;
    }
    if (!sku.factor || Number(sku.factor) < 2) {
      ElMessage.warning(`包装单位「${sku.specName}」需包含至少 2 个主单位`);
      return;
    }
  }
  const unit = form.unit.trim();
  const payload = {
    categoryId: form.categoryId,
    name: form.name,
    unit,
    cover: form.cover,
    images: form.images,
    description: form.description,
    v3GiftEnabled: form.v3GiftEnabled,
    recommend: form.recommend,
    status: form.status,
    sort: form.sort,
    skus: form.skus.map((s) => ({
      id: s.id,
      specName: s.isBase ? unit : s.specName.trim(),
      factor: s.isBase ? 1 : Number(s.factor) || 1,
      sort: s.sort || 1,
      prices: sortedLevels.value.map((l) => ({
        levelId: l.id,
        price: yuan2fen(s.priceYuan[l.id] || 0)
      }))
    }))
  };
  if (form.v3GiftEnabled === 1) {
    if (!giftRule.specName) {
      ElMessage.warning('请选择买赠的赠品规格');
      return;
    }
    const threshold = Number(giftRule.thresholdQty);
    const giftQty = Number(giftRule.giftQty);
    if (!threshold || threshold < 1 || !giftQty || giftQty < 1) {
      ElMessage.warning('买赠数量需为大于 0 的整数');
      return;
    }
  }
  saving.value = true;
  try {
    const saved = editing.value
      ? await productApi.update(editing.value.id, payload)
      : await productApi.create(payload);
    await saveGiftRule(saved);
    ElMessage.success('已保存');
    dialogVisible.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

/**
 * 商品保存后同步买赠规则：表单里选的是规格名称，需要映射成保存后的 skuId。
 * 关闭买赠时停用已有规则，不删除历史配置。
 */
async function saveGiftRule(saved) {
  const skus = (saved && saved.skus) || [];
  if (form.v3GiftEnabled !== 1) {
    if (giftRule.id) {
      await giftRuleApi.setEnabled(giftRule.id, false).catch(() => {});
    }
    return;
  }
  const target = giftRule.specName === '__base__'
    ? skus[0]
    : skus.find((s) => s.specName === giftRule.specName);
  if (!target || !target.id) {
    ElMessage.warning('赠品规格保存失败，请重新选择规格后保存');
    return;
  }
  const rulePayload = {
    productId: saved.id,
    giftSkuId: target.id,
    thresholdQty: Number(giftRule.thresholdQty) || 1,
    giftQty: Number(giftRule.giftQty) || 1,
    enabled: 1,
    startTime: giftRule.timeRange && giftRule.timeRange[0] ? giftRule.timeRange[0] : null,
    endTime: giftRule.timeRange && giftRule.timeRange[1] ? giftRule.timeRange[1] : null
  };
  if (giftRule.id) {
    await giftRuleApi.update(giftRule.id, rulePayload);
  } else {
    await giftRuleApi.create(rulePayload);
  }
}

async function onRemove(row) {
  try {
    await ElMessageBox.confirm(`确定删除商品「${row.name}」吗？`, '提示', { type: 'warning' });
  } catch (e) {
    return;
  }
  await productApi.remove(row.id);
  ElMessage.success('已删除');
  load();
}

async function showDetail(row) {
  detailRow.value = await productApi.detail(row.id);
  detailVisible.value = true;
}

async function ensureLevels() {
  if (!levels.value.length) levels.value = (await levelApi.list()) || [];
}

onMounted(async () => {
  const [cats] = await Promise.all([categoryApi.list(), ensureLevels()]);
  categories.value = cats || [];
  load();
});
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">商品档案</h2>
      <div>
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="openCreate">新增商品</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-form class="filter-bar" inline>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="商品名称" clearable style="width: 200px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.categoryId" clearable placeholder="全部" style="width: 150px">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 110px">
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="封面" width="80">
          <template #default="{ row }">
            <el-image v-if="row.cover" :src="row.cover" class="thumb" :preview-src-list="[row.cover]" />
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="160" />
        <el-table-column label="分类" width="110">
          <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="标记" width="160">
          <template #default="{ row }">
            <el-tag v-if="row.recommend" type="warning" size="small">推荐</el-tag>
            <el-tag v-if="row.v3GiftEnabled" type="success" size="small">最高档买赠</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">查看</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="load"
          @size-change="search"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑商品' : '新增商品'"
      width="960px"
      top="4vh"
    >
      <el-form :model="form" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="商品名称" required>
              <el-input v-model="form.name" maxlength="64" placeholder="请输入商品名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品分组" required>
              <el-select v-model="form.categoryId" placeholder="请选择商品分组" style="width: 100%">
                <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="主单位" required>
              <el-input v-model="form.unit" maxlength="8" placeholder="如 瓶 / 袋 / 盒" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否推荐">
              <el-switch v-model="form.recommend" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否买赠">
              <el-switch v-model="form.v3GiftEnabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-card v-if="form.v3GiftEnabled === 1" shadow="never" class="gift-box">
          <div class="gift-box__title">买赠规则</div>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="赠品规格">
                <el-select v-model="giftRule.specName" placeholder="请选择赠品规格" style="width: 100%">
                  <el-option
                    v-for="opt in specOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="购买数量">
                <el-input v-model="giftRule.thresholdQty" placeholder="满几个主单位" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="赠送数量">
                <el-input v-model="giftRule.giftQty" placeholder="赠几个" />
              </el-form-item>
            </el-col>
            <el-col :span="20">
              <el-form-item label="活动时间">
                <el-date-picker
                  v-model="giftRule.timeRange"
                  type="datetimerange"
                  range-separator="至"
                  start-placeholder="开始时间"
                  end-placeholder="结束时间"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <p class="gift-box__tip">购买满「购买数量」个主单位，自动赠送「赠送数量」个所选规格；活动时间留空表示长期有效。</p>
        </el-card>

        <div class="media-row">
          <div class="media-item">
            <div class="media-label">封面图</div>
            <ImageUploader v-model="form.cover" :limit="1" mode="single" tip="建议 800x800，jpg/png/webp" />
          </div>
          <div class="media-item">
            <div class="media-label">轮播图</div>
            <ImageUploader v-model="form.images" :limit="6" tip="最多 6 张，首张为列表主图" />
          </div>
        </div>

        <el-form-item label="商品描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="2000" show-word-limit />
        </el-form-item>

        <el-divider content-position="left">多规格与档位价格（元）</el-divider>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mb8"
          title="先填主单位，再添加多规格：1 个规格 = N 个主单位（如 1 箱 = 12 瓶），价格按各自单位分别填写。"
        />
        <el-table :data="form.skus" border size="small">
          <el-table-column label="规格名称" min-width="160">
            <template #default="{ row }">
              <div v-if="row.isBase" class="base-unit">
                <el-tag size="small" type="success">主单位</el-tag>
                <span class="base-unit__name">{{ form.unit || '请先填写主单位' }}</span>
              </div>
              <el-input v-else v-model="row.specName" placeholder="如 箱 / 件" />
            </template>
          </el-table-column>
          <el-table-column label="换算关系" width="150">
            <template #default="{ row }">
              <span v-if="row.isBase">1 {{ form.unit || '主单位' }}</span>
              <el-input-number
                v-else
                v-model="row.factor"
                :min="2"
                size="small"
                style="width: 96px"
              />
            </template>
          </el-table-column>
          <el-table-column label="排序" width="90">
            <template #default="{ row }">
              <el-input-number v-model="row.sort" :min="1" size="small" style="width: 70px" />
            </template>
          </el-table-column>
          <el-table-column v-for="l in sortedLevels" :key="l.id" :label="l.name" width="140">
            <template #default="{ row }">
              <el-input
                :model-value="row.priceYuan[l.id]"
                class="price-input"
                placeholder="0.00"
                @update:model-value="(v) => onPriceInput(row, l.id, v)"
              >
                <template #prefix>¥</template>
              </el-input>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row, $index }">
              <el-button v-if="!row.isBase" link type="danger" @click="removeSku($index)">删除</el-button>
              <span v-else class="text-muted">—</span>
            </template>
          </el-table-column>
        </el-table>
        <el-button class="mt8" plain @click="addSku">添加多规格</el-button>

        <el-row :gutter="16" class="extra-row">
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input v-model="form.sort" placeholder="数字越小越靠前" style="width: 160px" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上架状态">
              <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="上架" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="商品详情" width="760px">
      <template v-if="detailRow">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="ID">{{ detailRow.id }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ categoryName(detailRow.categoryId) }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ detailRow.name }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detailRow.status === 1 ? '上架' : '下架' }}</el-descriptions-item>
          <el-descriptions-item label="推荐">{{ detailRow.recommend ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="最高档买赠">{{ detailRow.v3GiftEnabled ? '开启' : '关闭' }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ detailRow.description || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="detailRow.skus || []" border size="small" class="mt16">
          <el-table-column prop="specName" label="规格" min-width="120" />
          <el-table-column prop="factor" label="系数" width="70" />
          <el-table-column v-for="l in sortedLevels" :key="l.id" :label="l.name" width="110">
            <template #default="{ row }">¥{{ priceOf(row, l.id) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.gift-box {
  margin: 0 0 16px;
  background: #f7faf7;
  border: 1px solid #e4eee4;
}

.gift-box__title {
  font-size: 14px;
  font-weight: 600;
  color: #326940;
  margin-bottom: 12px;
}

.gift-box__tip {
  margin: 0;
  font-size: 12px;
  color: #8a9099;
}

.price-input {
  width: 118px;
}

.media-row {
  display: flex;
  gap: 24px;
  margin-bottom: 18px;
  padding-left: 110px;
}

.media-item {
  flex: 1;
  min-width: 0;
}

.media-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}

.media-row {
  display: flex;
  gap: 24px;
  margin-bottom: 18px;
  padding-left: 110px;
}

.media-item {
  flex: 1;
  min-width: 0;
}

.media-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}

.extra-row {
  margin-top: 16px;
}
</style>

<style scoped>
.gift-box {
  margin: 0 0 16px;
  background: #f7faf7;
  border: 1px solid #e4eee4;
}

.gift-box__title {
  font-size: 14px;
  font-weight: 600;
  color: #326940;
  margin-bottom: 12px;
}

.gift-box__tip {
  margin: 0;
  font-size: 12px;
  color: #8a9099;
}

.price-input {
  width: 118px;
}

.media-row {
  display: flex;
  gap: 24px;
  margin-bottom: 18px;
  padding-left: 110px;
}

.media-item {
  flex: 1;
  min-width: 0;
}

.media-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}

.media-row {
  display: flex;
  gap: 24px;
  margin-bottom: 18px;
  padding-left: 110px;
}

.media-item {
  flex: 1;
  min-width: 0;
}

.media-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}

.extra-row {
  margin-top: 16px;
}
</style>
