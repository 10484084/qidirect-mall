<template>
  <van-tabbar v-model="active" route safe-area-inset-bottom active-color="#1f6feb" inactive-color="#8a9099">
    <van-tabbar-item to="/" icon="home-o">首页</van-tabbar-item>
    <van-tabbar-item to="/products" icon="apps-o">商品</van-tabbar-item>
    <van-tabbar-item to="/cart" icon="shopping-cart-o" :badge="cart.count || ''">购物车</van-tabbar-item>
    <van-tabbar-item to="/orders" icon="orders-o">订单</van-tabbar-item>
    <van-tabbar-item to="/mine" icon="user-o">我的</van-tabbar-item>
  </van-tabbar>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useCartStore } from '../stores/cart';
import { getToken } from '../utils/token';

const active = ref(0);
const cart = useCartStore();

onMounted(() => {
  if (getToken()) cart.refresh().catch(() => {});
});
</script>
