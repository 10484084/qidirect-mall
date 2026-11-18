<template>
  <van-nav-bar
    :title="title"
    :left-arrow="back"
    fixed
    placeholder
    @click-left="onBack"
  >
    <template v-if="$slots.right" #right>
      <slot name="right" />
    </template>
  </van-nav-bar>
</template>

<script setup>
import { useRouter } from 'vue-router';

const props = defineProps({
  title: { type: String, default: '' },
  back: { type: Boolean, default: true }
});
const router = useRouter();

function onBack() {
  if (props.back) router.back();
}
</script>
