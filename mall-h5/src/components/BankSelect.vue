<template>
  <van-field
    v-bind="$attrs"
    :model-value="modelValue"
    readonly
    is-link
    :placeholder="placeholder"
    @click="visible = true"
  />

  <van-popup v-model:show="visible" position="bottom" round :style="{ height: '72%' }">
    <div class="bank-picker">
      <div class="bank-picker__head">
        <span class="bank-picker__title">选择开户行</span>
        <van-icon name="cross" size="18" color="#969799" @click="visible = false" />
      </div>
      <van-search v-model="keyword" placeholder="搜索银行名称，如 工商" shape="round" />
      <div class="bank-list">
        <van-cell
          v-for="b in filtered"
          :key="b"
          :title="b"
          clickable
          :class="{ 'bank-item--active': b === modelValue }"
          @click="pick(b)"
        />
        <div v-if="!filtered.length" class="bank-empty">未找到匹配的银行</div>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { computed, ref } from 'vue';
import { BANK_NAMES } from '../utils/banks';

defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '请选择开户行' }
});
const emit = defineEmits(['update:modelValue']);

const visible = ref(false);
const keyword = ref('');

const filtered = computed(() => {
  const kw = keyword.value.trim();
  if (!kw) return BANK_NAMES;
  return BANK_NAMES.filter((b) => b.includes(kw));
});

function pick(name) {
  emit('update:modelValue', name);
  visible.value = false;
  keyword.value = '';
}
</script>

<style scoped>
.bank-picker {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.bank-picker__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 6px;
}

.bank-picker__title {
  font-size: 16px;
  font-weight: 600;
}

.bank-list {
  flex: 1;
  overflow-y: auto;
  padding-bottom: env(safe-area-inset-bottom);
}

.bank-item--active :deep(.van-cell__title) {
  color: var(--brand);
  font-weight: 600;
}

.bank-empty {
  padding: 40px 0;
  text-align: center;
  font-size: 13px;
  color: #969799;
}
</style>
