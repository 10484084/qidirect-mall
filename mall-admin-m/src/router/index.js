import { createRouter, createWebHashHistory } from 'vue-router';
import { useUserStore } from '../stores/user';

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('../views/DashboardView.vue'),
    meta: { title: '工作台', tab: 'dashboard' }
  },
  {
    path: '/orders',
    name: 'orders',
    component: () => import('../views/trade/OrderListView.vue'),
    meta: { title: '订单', tab: 'orders' }
  },
  {
    path: '/orders/:id',
    name: 'order-detail',
    component: () => import('../views/trade/OrderDetailView.vue'),
    meta: { title: '订单详情' }
  },
  {
    path: '/stores',
    name: 'stores',
    component: () => import('../views/store/StoreListView.vue'),
    meta: { title: '门店', tab: 'stores' }
  },
  {
    path: '/stores/:id',
    name: 'store-detail',
    component: () => import('../views/store/StoreDetailView.vue'),
    meta: { title: '门店详情' }
  },
  {
    path: '/withdraws',
    name: 'withdraws',
    component: () => import('../views/withdraw/WithdrawListView.vue'),
    meta: { title: '提现审核', tab: 'withdraws' }
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('../views/ProfileView.vue'),
    meta: { title: '我的', tab: 'profile' }
  },
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
});

router.beforeEach((to) => {
  const userStore = useUserStore();
  if (to.meta.public) {
    if (to.name === 'login' && userStore.isLogin) {
      return { path: '/dashboard' };
    }
    return true;
  }
  if (!userStore.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  return true;
});

router.afterEach((to) => {
  const title = to.meta.title;
  document.title = title ? `${title} · 厂家后台` : '厂家后台';
});

export default router;
