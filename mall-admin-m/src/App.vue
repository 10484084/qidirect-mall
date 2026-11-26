<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const showTabbar = computed(() => !!route.meta.tab);
</script>

<template>
  <div class="app-root">
    <router-view v-slot="{ Component }">
      <keep-alive :include="['ProductListView', 'StoreListView', 'WithdrawListView', 'OrderListView', 'DashboardView']">
        <component :is="Component" />
      </keep-alive>
    </router-view>

    <van-tabbar v-if="showTabbar" route safe-area-inset-bottom active-color="#326940" inactive-color="#8a9099">
      <van-tabbar-item replace to="/dashboard" icon="chart-trending-o">数据</van-tabbar-item>
      <van-tabbar-item replace to="/products" icon="gift-o">商品</van-tabbar-item>
      <van-tabbar-item replace to="/orders" icon="orders-o">订单</van-tabbar-item>
      <van-tabbar-item replace to="/stores" icon="shop-o">门店</van-tabbar-item>
      <van-tabbar-item replace to="/profile" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>
