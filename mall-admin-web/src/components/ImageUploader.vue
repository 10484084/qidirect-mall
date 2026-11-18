<template>
  <div class="image-uploader">
    <el-upload
      :file-list="currentFiles()"
      :http-request="httpRequest"
      :on-success="onSuccess"
      :on-remove="onRemove"
      :limit="limit"
      :accept="accept"
      list-type="picture-card"
      multiple
    >
      <el-icon><Plus /></el-icon>
    </el-upload>
    <div v-if="tip" class="text-muted uploader-tip">{{ tip }}</div>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import { httpRequest } from '../utils/upload';

const props = defineProps({
  modelValue: { type: [Array, String], default: () => [] },
  limit: { type: Number, default: 5 },
  accept: { type: String, default: 'image/*' },
  tip: { type: String, default: '' },
  /** single：modelValue 为字符串（封面等单图场景） */
  mode: { type: String, default: 'multiple' }
});
const emit = defineEmits(['update:modelValue']);

function normalize(list) {
  if (!list) return [];
  return Array.isArray(list) ? list.filter(Boolean) : [list];
}

function emitList(list) {
  emit('update:modelValue', props.mode === 'single' ? list[0] || '' : list);
}

function currentFiles() {
  return normalize(props.modelValue).map((url) => ({ name: url.split('/').pop(), url }));
}

function onSuccess(response, uploadFile) {
  emitList([...normalize(props.modelValue), uploadFile.url]);
  ElMessage.success('上传成功');
}

function onRemove(uploadFile) {
  const list = normalize(props.modelValue).slice();
  const idx = currentFiles().findIndex((f) => f.url === uploadFile.url);
  if (idx >= 0) {
    list.splice(idx, 1);
    emitList(list);
  }
}
</script>

<style scoped>
.uploader-tip {
  font-size: 12px;
  line-height: 1.5;
}
</style>
