<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { storeApi } from '../api';

const props = defineProps({
  modelValue: { type: [Number, String], default: null },
  placeholder: { type: String, default: '输入门店名称/手机号搜索' },
  clearable: { type: Boolean, default: true }
});
const emit = defineEmits(['update:modelValue', 'change']);

const options = ref([]);
const loading = ref(false);
const selectedLabel = ref('');

async function search(keyword) {
  if (!keyword) {
    options.value = [];
    return;
  }
  loading.value = true;
  try {
    const res = await storeApi.page({ keyword, page: 1, size: 20 });
    options.value = (res && res.records) || [];
  } catch (e) {
    options.value = [];
  } finally {
    loading.value = false;
  }
}

function onSelect(id) {
  const hit = options.value.find((s) => s.id === id);
  selectedLabel.value = hit ? hit.name || hit.nickname || hit.phone : '';
  emit('change', hit || null);
}

watch(
  () => props.modelValue,
  async (val) => {
    if (!val) {
      selectedLabel.value = '';
      return;
    }
    if (options.value.some((s) => s.id === val)) return;
    try {
      const detail = await storeApi.detail(val);
      const profile = (detail && detail.profile) || {};
      selectedLabel.value = profile.name || profile.nickname || profile.phone || `#${val}`;
      options.value = [
        { id: val, name: profile.name, nickname: profile.nickname, phone: profile.phone }
      ];
    } catch (e) {
      /* 忽略 */
    }
  },
  { immediate: true }
);

onMounted(() => {
  if (props.modelValue) search('');
});
</script>

<template>
  <el-select
    :model-value="modelValue"
    filterable
    remote
    reserve-keyword
    :remote-method="search"
    :loading="loading"
    :placeholder="placeholder"
    :clearable="clearable"
    @update:model-value="emit('update:modelValue', $event)"
    @change="onSelect"
  >
    <el-option
      v-for="s in options"
      :key="s.id"
      :label="`${s.name || s.nickname || '未命名'} (${s.phone})`"
      :value="s.id"
    />
  </el-select>
</template>
