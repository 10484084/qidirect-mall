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


// 排序弹窗：左分类、右该分类商品，均可拖拽
const sortVisible = ref(false);
const sortCategories = ref([]);
const sortProducts = ref([]);
const sortCategoryId = ref(null);
const sortSaving = ref(false);
const dragIndex = ref(-1);
const dragType = ref('');

async function openSort() {
  sortVisible.value = true;
  const list = (await categoryApi.list()) || [];
  sortCategories.value = list.map((c) => ({ ...c }));
  if (!sortCategories.value.length) {
    sortProducts.value = [];
    return;
  }
  if (!sortCategoryId.value || !sortCategories.value.some((c) => c.id === sortCategoryId.value)) {
    sortCategoryId.value = sortCategories.value[0].id;
  }
  await loadSortProducts();
}

async function loadSortProducts() {
  if (!sortCategoryId.value) {
    sortProducts.value = [];
    return;
  }
  const res = await productApi.page({ categoryId: sortCategoryId.value, page: 1, size: 200 });
  const rows = (res && res.records) || [];
  sortProducts.value = rows
    .slice()
    .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
    .map((r) => ({ id: r.id, name: r.name, cover: r.cover }));
}

function pickCategory(id) {
  sortCategoryId.value = id;
  loadSortProducts();
}

function onDragStart(type, index) {
  dragType.value = type;
  dragIndex.value = index;
}

function onDrop(type, index) {
  if (dragType.value !== type || dragIndex.value < 0 || dragIndex.value === index) {
    return;
  }
  const list = type === 'category' ? sortCategories.value : sortProducts.value;
  const [moved] = list.splice(dragIndex.value, 1);
  list.splice(index, 0, moved);
  dragIndex.value = -1;
  dragType.value = '';
}

async function saveSort() {
  if (!sortCategories.value.length) {
    sortVisible.value = false;
    return;
  }
  sortSaving.value = true;
  try {
    await categoryApi.sort(sortCategories.value.map((c) => c.id));
    if (sortProducts.value.length) {
      await productApi.sort(sortProducts.value.map((p) => p.id));
    }
    ElMessage.success('排序已保存');
    sortVisible.value = false;
    load();
    if (query.categoryId) {
      categories.value = (await categoryApi.list()) || [];
    }
  } finally {
    sortSaving.value = false;
  }
}

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

const giftRules = ref([]);
const removedGiftRuleIds = ref([]);

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

function emptyGiftRule() {
  return { id: null, thresholdQty: 1, thresholdSpecName: '__base__', giftQty: 1, specName: '__base__' };
}

function addGiftRule() {
  giftRules.value.push(emptyGiftRule());
}

function removeGiftRule(index) {
  const rule = giftRules.value[index];
  if (rule && rule.id) {
    removedGiftRuleIds.value.push(rule.id);
  }
  giftRules.value.splice(index, 1);
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
  giftRules.value = [];
  removedGiftRuleIds.value = [];
  dialogVisible.value = true;
  await ensureLevels();
  form.skus = [emptySku(true)];
}

async function loadGiftRule(productId) {
  giftRules.value = [];
  removedGiftRuleIds.value = [];
  try {
    const rules = (await giftRuleApi.list(productId)) || [];
    const skus = form.skus || [];
    giftRules.value = rules
      .filter((r) => r.enabled === 1)
      .map((r) => {
        const matched = skus.find((sku) => sku.id === r.giftSkuId);
        const thresholdSku = (!r.thresholdSkuId || r.thresholdSkuId === 0)
          ? skus[0]
          : skus.find((sku) => sku.id === r.thresholdSkuId);
        return {
          id: r.id,
          thresholdQty: r.thresholdQty,
          thresholdSpecName: thresholdSku ? (thresholdSku.isBase ? '__base__' : thresholdSku.specName) : '__base__',
          giftQty: r.giftQty,
          specName: matched ? (matched.isBase ? '__base__' : matched.specName) : '__base__'
        };
      });
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
    skus: form.skus.map((s, index) => ({
      id: s.id,
      specName: s.isBase ? unit : s.specName.trim(),
      factor: s.isBase ? 1 : Number(s.factor) || 1,
      sort: index + 1,
      prices: sortedLevels.value.map((l) => ({
        levelId: l.id,
        price: yuan2fen(s.priceYuan[l.id] || 0)
      }))
    }))
  };
  if (form.v3GiftEnabled === 1) {
    if (!giftRules.value.length) {
      ElMessage.warning('请至少添加一条买赠规则');
      return;
    }
    for (let i = 0; i < giftRules.value.length; i += 1) {
      const r = giftRules.value[i];
      if (!r.thresholdSpecName || !r.specName) {
        ElMessage.warning(`第 ${i + 1} 条买赠规则未选择规格`);
        return;
      }
      const threshold = Number(r.thresholdQty);
      const giftQty = Number(r.giftQty);
      if (!threshold || threshold < 1 || !giftQty || giftQty < 1) {
        ElMessage.warning(`第 ${i + 1} 条买赠数量需为大于 0 的整数`);
        return;
      }
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
    for (const rule of giftRules.value) {
      if (rule.id) await giftRuleApi.setEnabled(rule.id, false).catch(() => {});
    }
    return;
  }
  for (const id of removedGiftRuleIds.value) {
    await giftRuleApi.remove(id).catch(() => {});
  }
  for (const rule of giftRules.value) {
    const target = rule.specName === '__base__'
      ? skus[0]
      : skus.find((sku) => sku.specName === rule.specName);
    const thresholdTarget = rule.thresholdSpecName === '__base__'
      ? skus[0]
      : skus.find((sku) => sku.specName === rule.thresholdSpecName);
    if (!target || !target.id || !thresholdTarget || !thresholdTarget.id) {
      ElMessage.warning('买赠规格保存失败，请重新选择规格后保存');
      return;
    }
    const payload = {
      productId: saved.id,
      thresholdSkuId: thresholdTarget.id,
      giftSkuId: target.id,
      thresholdQty: Number(rule.thresholdQty) || 1,
      giftQty: Number(rule.giftQty) || 1,
      enabled: 1,
      startTime: null,
      endTime: null
    };
    if (rule.id) await giftRuleApi.update(rule.id, payload);
    else await giftRuleApi.create(payload);
  }
  removedGiftRuleIds.value = [];
}

async function toggleStatus(row, value) {
  const next = value ? 1 : 0;
  row.status = next;
  await productApi.setStatus(row.id, next);
  ElMessage.success(next === 1 ? '已上架' : '已下架');
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
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 130px">
            <el-option label="上架商品" :value="1" />
            <el-option label="下架商品" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="openSort">排序</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border stripe class="center-table">
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
        <el-table-column label="标记" width="160">
          <template #default="{ row }">
            <el-tag v-if="row.recommend" type="warning" size="small">推荐</el-tag>
            <el-tag v-if="row.v3GiftEnabled" type="success" size="small">最高档买赠</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center" header-align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              inline-prompt
              active-text="上架"
              inactive-text="下架"
              @change="(val) => toggleStatus(row, val)"
            />
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
            <el-form-item label="是否参与5万档买赠" label-width="150px">
              <el-switch v-model="form.v3GiftEnabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-card v-if="form.v3GiftEnabled === 1" shadow="never" class="gift-box">
          <div class="gift-box__head">
            <span class="gift-box__title">买赠规则</span>
            <el-button link type="primary" @click="addGiftRule">+ 新增规则</el-button>
          </div>
          <div v-for="(rule, idx) in giftRules" :key="idx" class="gift-row">
            <span class="gift-row__label">买</span>
            <el-input v-model="rule.thresholdQty" class="gift-qty" placeholder="数量" />
            <el-select v-model="rule.thresholdSpecName" class="gift-spec" placeholder="规格">
              <el-option v-for="opt in specOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <span class="gift-row__label">送</span>
            <el-input v-model="rule.giftQty" class="gift-qty" placeholder="数量" />
            <el-select v-model="rule.specName" class="gift-spec" placeholder="规格">
              <el-option v-for="opt in specOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-button link type="danger" @click="removeGiftRule(idx)">删除</el-button>
          </div>
          <div v-if="!giftRules.length" class="gift-box__empty">尚未添加规则，点击右上角「+ 新增规则」</div>
          <p class="gift-box__tip">门槛规格会按换算关系折算成主单位后与购买量比较；配置多条时，按已达成的最高门槛执行。例：买 2 箱（20 瓶）送 1 瓶。</p>
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
        <el-table :data="form.skus" border size="small" class="center-table">
          <el-table-column label="规格名称" min-width="170" align="center" header-align="center">
            <template #default="{ row }">
              <div v-if="row.isBase" class="unit-cell">
                <el-tag size="small" type="success" class="unit-tag">主单位</el-tag>
                <span class="unit-name">{{ form.unit || '未填写' }}</span>
              </div>
              <div v-else class="unit-cell">
                <el-tag size="small" type="info" class="unit-tag">辅单位</el-tag>
                <el-input v-model="row.specName" class="unit-name-input" placeholder="如 箱 / 件" />
              </div>
            </template>
          </el-table-column>
          <el-table-column label="换算关系" width="140" align="center" header-align="center">
            <template #default="{ row }">
              <span v-if="row.isBase" class="center-text">1</span>
              <el-input v-else v-model="row.factor" class="factor-input" placeholder="2" />
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


      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sortVisible" title="排序" width="860px" top="6vh">
      <div class="sort-wrap">
        <div class="sort-col sort-col--category">
          <div class="sort-col__title">商品分类</div>
          <div class="sort-list">
            <div
              v-for="(cat, idx) in sortCategories"
              :key="cat.id"
              class="sort-item"
              :class="{ active: cat.id === sortCategoryId }"
              draggable="true"
              @dragstart="onDragStart('category', idx)"
              @dragover.prevent
              @drop="onDrop('category', idx)"
              @click="pickCategory(cat.id)"
            >
              <span class="drag-handle">⋮⋮</span>
              <span class="sort-item__name">{{ cat.name }}</span>
            </div>
          </div>
        </div>
        <div class="sort-col sort-col--product">
          <div class="sort-col__title">
            分类内商品
            <span class="sort-col__hint">拖拽调整显示顺序</span>
          </div>
          <div class="sort-list">
            <div
              v-for="(p, idx) in sortProducts"
              :key="p.id"
              class="sort-item"
              draggable="true"
              @dragstart="onDragStart('product', idx)"
              @dragover.prevent
              @drop="onDrop('product', idx)"
            >
              <span class="drag-handle">⋮⋮</span>
              <span class="sort-item__name">{{ p.name }}</span>
            </div>
            <div v-if="!sortProducts.length" class="sort-empty">该分类下暂无商品</div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="sortVisible = false">取消</el-button>
        <el-button type="primary" :loading="sortSaving" @click="saveSort">保存排序</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sortVisible" title="排序" width="860px" top="6vh">
      <div class="sort-wrap">
        <div class="sort-col sort-col--category">
          <div class="sort-col__title">商品分类</div>
          <div class="sort-list">
            <div
              v-for="(cat, idx) in sortCategories"
              :key="cat.id"
              class="sort-item"
              :class="{ active: cat.id === sortCategoryId }"
              draggable="true"
              @dragstart="onDragStart('category', idx)"
              @dragover.prevent
              @drop="onDrop('category', idx)"
              @click="pickCategory(cat.id)"
            >
              <span class="drag-handle">⋮⋮</span>
              <span class="sort-item__name">{{ cat.name }}</span>
            </div>
          </div>
        </div>
        <div class="sort-col sort-col--product">
          <div class="sort-col__title">
            分类内商品
            <span class="sort-col__hint">拖拽调整显示顺序</span>
          </div>
          <div class="sort-list">
            <div
              v-for="(p, idx) in sortProducts"
              :key="p.id"
              class="sort-item"
              draggable="true"
              @dragstart="onDragStart('product', idx)"
              @dragover.prevent
              @drop="onDrop('product', idx)"
            >
              <span class="drag-handle">⋮⋮</span>
              <span class="sort-item__name">{{ p.name }}</span>
            </div>
            <div v-if="!sortProducts.length" class="sort-empty">该分类下暂无商品</div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="sortVisible = false">取消</el-button>
        <el-button type="primary" :loading="sortSaving" @click="saveSort">保存排序</el-button>
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
        <el-table :data="detailRow.skus || []" border size="small" class="mt16 center-table">
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

.gift-box__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.gift-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.gift-row__label {
  font-size: 14px;
  color: #606266;
}

.gift-row__unit {
  min-width: 44px;
  font-size: 14px;
  color: #606266;
  text-align: center;
}

.gift-qty {
  width: 88px;
}

.gift-spec {
  width: 160px;
}

.gift-box__empty {
  font-size: 13px;
  color: #a8abb2;
  padding: 6px 0;
}

.unit-tag {
  width: 52px;
  justify-content: center;
  margin-right: 6px;
}

.unit-tag {
  width: 52px;
  justify-content: center;
  margin-right: 6px;
}

.unit-name {
  font-size: 13px;
  color: #303133;
}

.unit-tag {
  width: 52px;
  justify-content: center;
  margin-right: 6px;
}

.unit-tag {
  width: 52px;
  justify-content: center;
  margin-right: 6px;
}

.unit-name {
  font-size: 13px;
  color: #303133;
}

.unit-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.unit-name-input {
  width: 100px;
}

.center-text {
  display: inline-block;
  width: 100%;
  text-align: center;
}

.factor-input {
  width: 90px;
}

.gift-box__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.gift-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.gift-row__label {
  font-size: 14px;
  color: #606266;
}

.gift-row__unit {
  min-width: 44px;
  font-size: 14px;
  color: #606266;
  text-align: center;
}

.gift-qty {
  width: 88px;
}

.gift-spec {
  width: 160px;
}

.gift-box__empty {
  font-size: 13px;
  color: #a8abb2;
  padding: 6px 0;
}

.unit-tag {
  width: 52px;
  justify-content: center;
  margin-right: 6px;
}

.unit-tag {
  width: 52px;
  justify-content: center;
  margin-right: 6px;
}

.unit-name {
  font-size: 13px;
  color: #303133;
}

.unit-tag {
  width: 52px;
  justify-content: center;
  margin-right: 6px;
}

.unit-tag {
  width: 52px;
  justify-content: center;
  margin-right: 6px;
}

.unit-name {
  font-size: 13px;
  color: #303133;
}

.unit-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.unit-name-input {
  width: 100px;
}

.center-text {
  display: inline-block;
  width: 100%;
  text-align: center;
}

.factor-input {
  width: 90px;
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

.sort-wrap {
  display: flex;
  gap: 16px;
  height: 460px;
}

.sort-col {
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}

.sort-col--category {
  width: 260px;
  flex-shrink: 0;
}

.sort-col--product {
  flex: 1;
  min-width: 0;
}

.sort-col__title {
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  background: #f7f8fa;
  border-bottom: 1px solid #ebeef5;
}

.sort-col__hint {
  margin-left: 8px;
  font-weight: 400;
  color: #a8abb2;
}

.sort-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.sort-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 6px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  cursor: pointer;
  user-select: none;
}

.sort-item:hover {
  border-color: #b9d3be;
}

.sort-item.active {
  border-color: #326940;
  background: #f2f8f3;
}

.drag-handle {
  color: #c0c4cc;
  cursor: move;
}

.sort-item__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sort-empty {
  padding: 20px;
  text-align: center;
  color: #a8abb2;
  font-size: 13px;
}

.sort-wrap {
  display: flex;
  gap: 16px;
  height: 460px;
}

.sort-col {
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}

.sort-col--category {
  width: 260px;
  flex-shrink: 0;
}

.sort-col--product {
  flex: 1;
  min-width: 0;
}

.sort-col__title {
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  background: #f7f8fa;
  border-bottom: 1px solid #ebeef5;
}

.sort-col__hint {
  margin-left: 8px;
  font-weight: 400;
  color: #a8abb2;
}

.sort-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.sort-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 6px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  cursor: pointer;
  user-select: none;
}

.sort-item:hover {
  border-color: #b9d3be;
}

.sort-item.active {
  border-color: #326940;
  background: #f2f8f3;
}

.drag-handle {
  color: #c0c4cc;
  cursor: move;
}

.sort-item__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sort-empty {
  padding: 20px;
  text-align: center;
  color: #a8abb2;
  font-size: 13px;
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

.sort-wrap {
  display: flex;
  gap: 16px;
  height: 460px;
}

.sort-col {
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}

.sort-col--category {
  width: 260px;
  flex-shrink: 0;
}

.sort-col--product {
  flex: 1;
  min-width: 0;
}

.sort-col__title {
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  background: #f7f8fa;
  border-bottom: 1px solid #ebeef5;
}

.sort-col__hint {
  margin-left: 8px;
  font-weight: 400;
  color: #a8abb2;
}

.sort-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.sort-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 6px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  cursor: pointer;
  user-select: none;
}

.sort-item:hover {
  border-color: #b9d3be;
}

.sort-item.active {
  border-color: #326940;
  background: #f2f8f3;
}

.drag-handle {
  color: #c0c4cc;
  cursor: move;
}

.sort-item__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sort-empty {
  padding: 20px;
  text-align: center;
  color: #a8abb2;
  font-size: 13px;
}

.sort-wrap {
  display: flex;
  gap: 16px;
  height: 460px;
}

.sort-col {
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}

.sort-col--category {
  width: 260px;
  flex-shrink: 0;
}

.sort-col--product {
  flex: 1;
  min-width: 0;
}

.sort-col__title {
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  background: #f7f8fa;
  border-bottom: 1px solid #ebeef5;
}

.sort-col__hint {
  margin-left: 8px;
  font-weight: 400;
  color: #a8abb2;
}

.sort-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.sort-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 6px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  cursor: pointer;
  user-select: none;
}

.sort-item:hover {
  border-color: #b9d3be;
}

.sort-item.active {
  border-color: #326940;
  background: #f2f8f3;
}

.drag-handle {
  color: #c0c4cc;
  cursor: move;
}

.sort-item__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sort-empty {
  padding: 20px;
  text-align: center;
  color: #a8abb2;
  font-size: 13px;
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

.gift-box__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.gift-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.gift-row__label {
  font-size: 14px;
  color: #606266;
}

.gift-row__unit {
  min-width: 44px;
  font-size: 14px;
  color: #606266;
  text-align: center;
}

.gift-qty {
  width: 88px;
}

.gift-spec {
  width: 160px;
}

.gift-box__empty {
  font-size: 13px;
  color: #a8abb2;
  padding: 6px 0;
}

.unit-tag {
  width: 52px;
  justify-content: center;
  margin-right: 6px;
}

.unit-tag {
  width: 52px;
  justify-content: center;
  margin-right: 6px;
}

.unit-name {
  font-size: 13px;
  color: #303133;
}

.unit-tag {
  width: 52px;
  justify-content: center;
  margin-right: 6px;
}

.unit-tag {
  width: 52px;
  justify-content: center;
  margin-right: 6px;
}

.unit-name {
  font-size: 13px;
  color: #303133;
}

.unit-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.unit-name-input {
  width: 100px;
}

.center-text {
  display: inline-block;
  width: 100%;
  text-align: center;
}

.factor-input {
  width: 90px;
}

.gift-box__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.gift-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.gift-row__label {
  font-size: 14px;
  color: #606266;
}

.gift-row__unit {
  min-width: 44px;
  font-size: 14px;
  color: #606266;
  text-align: center;
}

.gift-qty {
  width: 88px;
}

.gift-spec {
  width: 160px;
}

.gift-box__empty {
  font-size: 13px;
  color: #a8abb2;
  padding: 6px 0;
}

.unit-tag {
  width: 52px;
  justify-content: center;
  margin-right: 6px;
}

.unit-tag {
  width: 52px;
  justify-content: center;
  margin-right: 6px;
}

.unit-name {
  font-size: 13px;
  color: #303133;
}

.unit-tag {
  width: 52px;
  justify-content: center;
  margin-right: 6px;
}

.unit-tag {
  width: 52px;
  justify-content: center;
  margin-right: 6px;
}

.unit-name {
  font-size: 13px;
  color: #303133;
}

.unit-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.unit-name-input {
  width: 100px;
}

.center-text {
  display: inline-block;
  width: 100%;
  text-align: center;
}

.factor-input {
  width: 90px;
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

.sort-wrap {
  display: flex;
  gap: 16px;
  height: 460px;
}

.sort-col {
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}

.sort-col--category {
  width: 260px;
  flex-shrink: 0;
}

.sort-col--product {
  flex: 1;
  min-width: 0;
}

.sort-col__title {
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  background: #f7f8fa;
  border-bottom: 1px solid #ebeef5;
}

.sort-col__hint {
  margin-left: 8px;
  font-weight: 400;
  color: #a8abb2;
}

.sort-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.sort-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 6px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  cursor: pointer;
  user-select: none;
}

.sort-item:hover {
  border-color: #b9d3be;
}

.sort-item.active {
  border-color: #326940;
  background: #f2f8f3;
}

.drag-handle {
  color: #c0c4cc;
  cursor: move;
}

.sort-item__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sort-empty {
  padding: 20px;
  text-align: center;
  color: #a8abb2;
  font-size: 13px;
}

.sort-wrap {
  display: flex;
  gap: 16px;
  height: 460px;
}

.sort-col {
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}

.sort-col--category {
  width: 260px;
  flex-shrink: 0;
}

.sort-col--product {
  flex: 1;
  min-width: 0;
}

.sort-col__title {
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  background: #f7f8fa;
  border-bottom: 1px solid #ebeef5;
}

.sort-col__hint {
  margin-left: 8px;
  font-weight: 400;
  color: #a8abb2;
}

.sort-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.sort-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 6px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  cursor: pointer;
  user-select: none;
}

.sort-item:hover {
  border-color: #b9d3be;
}

.sort-item.active {
  border-color: #326940;
  background: #f2f8f3;
}

.drag-handle {
  color: #c0c4cc;
  cursor: move;
}

.sort-item__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sort-empty {
  padding: 20px;
  text-align: center;
  color: #a8abb2;
  font-size: 13px;
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

.sort-wrap {
  display: flex;
  gap: 16px;
  height: 460px;
}

.sort-col {
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}

.sort-col--category {
  width: 260px;
  flex-shrink: 0;
}

.sort-col--product {
  flex: 1;
  min-width: 0;
}

.sort-col__title {
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  background: #f7f8fa;
  border-bottom: 1px solid #ebeef5;
}

.sort-col__hint {
  margin-left: 8px;
  font-weight: 400;
  color: #a8abb2;
}

.sort-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.sort-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 6px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  cursor: pointer;
  user-select: none;
}

.sort-item:hover {
  border-color: #b9d3be;
}

.sort-item.active {
  border-color: #326940;
  background: #f2f8f3;
}

.drag-handle {
  color: #c0c4cc;
  cursor: move;
}

.sort-item__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sort-empty {
  padding: 20px;
  text-align: center;
  color: #a8abb2;
  font-size: 13px;
}

.sort-wrap {
  display: flex;
  gap: 16px;
  height: 460px;
}

.sort-col {
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}

.sort-col--category {
  width: 260px;
  flex-shrink: 0;
}

.sort-col--product {
  flex: 1;
  min-width: 0;
}

.sort-col__title {
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  background: #f7f8fa;
  border-bottom: 1px solid #ebeef5;
}

.sort-col__hint {
  margin-left: 8px;
  font-weight: 400;
  color: #a8abb2;
}

.sort-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.sort-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 6px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  cursor: pointer;
  user-select: none;
}

.sort-item:hover {
  border-color: #b9d3be;
}

.sort-item.active {
  border-color: #326940;
  background: #f2f8f3;
}

.drag-handle {
  color: #c0c4cc;
  cursor: move;
}

.sort-item__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sort-empty {
  padding: 20px;
  text-align: center;
  color: #a8abb2;
  font-size: 13px;
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
