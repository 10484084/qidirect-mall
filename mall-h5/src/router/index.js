import { createRouter, createWebHashHistory } from 'vue-router';
import { getToken, needChangePwd } from '../utils/token';

const routes = [
  { path: '/guide', name: 'guide', component: () => import('../views/GuideView.vue'), meta: { public: true } },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
  // 注册入口已隐藏：邀请注册由后台/线下完成，路由保留兜底重定向
  { path: '/register', redirect: '/login' },
  { path: '/change-password', name: 'change-password', component: () => import('../views/ChangePasswordView.vue') },
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue'), meta: { tab: true } },
  { path: '/products', name: 'products', component: () => import('../views/ProductsView.vue'), meta: { tab: true } },
  { path: '/products/:id', name: 'product-detail', component: () => import('../views/ProductDetailView.vue') },
  { path: '/cart', name: 'cart', component: () => import('../views/CartView.vue'), meta: { tab: true } },
  { path: '/checkout', name: 'checkout', component: () => import('../views/CheckoutView.vue') },
  { path: '/pay/:payNo', name: 'pay', component: () => import('../views/PayView.vue') },
  { path: '/orders', name: 'orders', component: () => import('../views/OrdersView.vue'), meta: { tab: true } },
  { path: '/orders/:id', name: 'order-detail', component: () => import('../views/OrderDetailView.vue') },
  { path: '/purchase', name: 'purchase', component: () => import('../views/PurchaseView.vue') },
  { path: '/mine', name: 'mine', component: () => import('../views/MineView.vue'), meta: { tab: true } },
  { path: '/profile', name: 'profile', component: () => import('../views/StoreProfileView.vue') },
  { path: '/auth-verify', name: 'auth-verify', component: () => import('../views/AuthVerifyView.vue') },
  { path: '/bind-bank', name: 'bind-bank', component: () => import('../views/BindBankView.vue') },
  { path: '/wallet', name: 'wallet', component: () => import('../views/WalletView.vue') },
  { path: '/withdraw', name: 'withdraw', component: () => import('../views/WithdrawView.vue') },
  { path: '/team', name: 'team', component: () => import('../views/TeamView.vue') },
  { path: '/performance', name: 'performance', component: () => import('../views/PerformanceView.vue') },
  { path: '/timeline', name: 'timeline', component: () => import('../views/TimelineView.vue') },
  { path: '/invite', name: 'invite', component: () => import('../views/InviteView.vue') },
  { path: '/notices', name: 'notices', component: () => import('../views/NoticesView.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/' }
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
});

router.beforeEach((to) => {
  if (!to.meta.public && !getToken()) {
    return { name: 'login', query: { redirect: to.fullPath } };
  }
  // 初始密码未修改前，除改密页外一律拦到改密页
  if (getToken() && needChangePwd() && to.name !== 'change-password' && !to.meta.public) {
    return { name: 'change-password' };
  }
  if (to.name === 'login' && getToken()) {
    return { path: needChangePwd() ? '/change-password' : '/' };
  }
  return true;
});

router.afterEach((to) => {
  const titles = {
    home: '直销商城',
    products: '商品',
    cart: '购物车',
    orders: '我的订单',
    mine: '我的',
    purchase: '加盟 / 预存',
    wallet: '我的钱包',
    withdraw: '提现',
    team: '我的团队',
    performance: '团队业绩',
    timeline: '历史记录',
    invite: '邀请好友',
    profile: '我的资料',
    'auth-verify': '实名认证',
    'bind-bank': '绑定收款卡',
    notices: '消息',
    login: '登录',
    'change-password': '修改密码',
    guide: '请在微信中打开'
  };
  document.title = titles[to.name] || '直销商城';
});

export default router;
