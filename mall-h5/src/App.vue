<template>
  <router-view />
  <TabBar v-if="showTab" />
</template>

<script setup>
import { computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import TabBar from './components/TabBar.vue';
import { useUserStore } from './stores/user';
import { getToken } from './utils/token';

const route = useRoute();
const user = useUserStore();
const showTab = computed(() => Boolean(route.meta.tab));

onMounted(() => {
  if (getToken()) {
    user.loadProfile().catch(() => {});
  }
});
</script>
