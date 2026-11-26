import { createRouter, createWebHashHistory } from 'vue-router';
import { useUserStore } from '../stores/user';

const Layout = () => import('../layouts/MainLayout.vue');

export const menuRoutes = [
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('../views/DashboardView.vue'),
    meta: { title: '工作台', icon: 'Odometer' }
  },
  {
    path: '/stores',
    meta: { title: '门店管理', icon: 'Shop' },
    children: [
      {
        path: '',
        name: 'stores',
        component: () => import('../views/store/StoreListView.vue'),
        meta: { title: '门店列表', icon: 'List' }
      },
      {
        path: 'tree',
        name: 'store-tree',
        component: () => import('../views/store/StoreTreeView.vue'),
        meta: { title: '门店树', icon: 'Share' }
      }
    ]
  },
  {
    path: '/goods',
    meta: { title: '档案管理', icon: 'Goods' },
    children: [
      {
        path: 'levels',
        name: 'levels',
        component: () => import('../views/goods/LevelView.vue'),
        meta: { title: '储值档位', icon: 'Medal' }
      },
      {
        path: 'categories',
        name: 'categories',
        component: () => import('../views/goods/CategoryView.vue'),
        meta: { title: '商品分类', icon: 'Files' }
      },
      {
        path: 'products',
        name: 'products',
        component: () => import('../views/goods/ProductView.vue'),
        meta: { title: '商品档案', icon: 'Present' }
      }
    ]
  },
  {
    path: '/trade',
    meta: { title: '交易管理', icon: 'ShoppingCart' },
    children: [
      {
        path: 'orders',
        name: 'orders',
        component: () => import('../views/trade/OrderView.vue'),
        meta: { title: '订单发货', icon: 'Tickets' }
      },
      {
        path: 'cancel-audit',
        name: 'cancel-audit',
        component: () => import('../views/trade/CancelAuditView.vue'),
        meta: { title: '取消审核', icon: 'RefreshLeft' }
      },
      {
        path: 'purchase',
        name: 'purchase',
        component: () => import('../views/trade/PurchaseView.vue'),
        meta: { title: '线下补款', icon: 'ShoppingBag' }
      },
      {
        path: 'wallet-adjust',
        name: 'wallet-adjust',
        component: () => import('../views/trade/WalletAdjustView.vue'),
        meta: { title: '余额调整', icon: 'Coin' }
      }
    ]
  },
  {
    path: '/performance',
    meta: { title: '业绩提成', icon: 'TrendCharts' },
    children: [
      {
        path: 'commission',
        name: 'commission',
        component: () => import('../views/performance/CommissionView.vue'),
        meta: { title: '推荐奖励', icon: 'Money' }
      },
      {
        path: 'performance',
        name: 'performance',
        component: () => import('../views/performance/PerformanceView.vue'),
        meta: { title: '团队奖励', icon: 'DataLine' }
      }
    ]
  },
  {
    path: '/withdraw',
    meta: { title: '提现', icon: 'CreditCard' },
    children: [
      {
        path: 'audit',
        name: 'withdraw-audit',
        component: () => import('../views/withdraw/WithdrawAuditView.vue'),
        meta: { title: '提现审核', icon: 'Stamp' }
      },
      {
        path: 'batches',
        name: 'withdraw-batches',
        component: () => import('../views/withdraw/WithdrawBatchView.vue'),
        meta: { title: '打款批次', icon: 'Collection' }
      }
    ]
  },
  {
    path: '/finance',
    meta: { title: '财务', icon: 'Wallet' },
    children: [
      {
        path: 'wallet-logs',
        name: 'wallet-logs',
        component: () => import('../views/finance/WalletLogView.vue'),
        meta: { title: '余额流水', icon: 'List' }
      },
      {
        path: 'payments',
        name: 'payments',
        component: () => import('../views/finance/PaymentView.vue'),
        meta: { title: '支付流水', icon: 'Ticket' }
      },
      {
        path: 'reconcile',
        name: 'reconcile',
        component: () => import('../views/finance/ReconcileView.vue'),
        meta: { title: '对账', icon: 'Checked' }
      },
      {
        path: 'abnormal',
        name: 'abnormal',
        component: () => import('../views/finance/AbnormalOrderView.vue'),
        meta: { title: '异常单处置', icon: 'Warning' }
      }
    ]
  },
  {
    path: '/system',
    meta: { title: '系统', icon: 'Setting' },
    children: [
      {
        path: 'notices',
        name: 'notices',
        component: () => import('../views/system/NoticeView.vue'),
        meta: { title: '通知发送', icon: 'Bell' }
      },
      {
        path: 'sms-templates',
        name: 'sms-templates',
        component: () => import('../views/system/SmsTemplateView.vue'),
        meta: { title: '短信模板', icon: 'ChatDotSquare' }
      },
      {
        path: 'admin-users',
        name: 'admin-users',
        component: () => import('../views/system/AdminUserView.vue'),
        meta: { title: '管理员', icon: 'User' }
      },
      {
        path: 'roles',
        name: 'roles',
        component: () => import('../views/system/RoleView.vue'),
        meta: { title: '角色权限', icon: 'Lock' }
      },
      {
        path: 'configs',
        name: 'configs',
        component: () => import('../views/system/ConfigView.vue'),
        meta: { title: '系统配置', icon: 'SetUp' }
      },
      {
        path: 'operation-logs',
        name: 'operation-logs',
        component: () => import('../views/system/OperationLogView.vue'),
        meta: { title: '操作日志', icon: 'Notebook' }
      }
    ]
  }
];

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: menuRoutes
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('../views/NotFoundView.vue'),
    meta: { title: '页面不存在' }
  }
];

const router = createRouter({
  history: createWebHashHistory(),
  routes
});

/**
 * 子路由未声明 perm 时，向上取父级路由的 perm。
 */
function findParentPerm(to) {
  const matched = to.matched || [];
  for (let i = matched.length - 1; i >= 0; i -= 1) {
    if (matched[i].meta && matched[i].meta.perm) return matched[i].meta.perm;
  }
  return null;
}

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
  const perm = to.meta.perm || findParentPerm(to);
  if (perm && !userStore.hasAnyPerm(perm)) {
    return { path: '/dashboard' };
  }
  if (to.meta.hidden) {
    return { path: '/dashboard' };
  }
  return true;
});

router.afterEach((to) => {
  const title = to.meta.title;
  document.title = title ? `${title} · 厂家后台` : '直销商城 · 厂家后台';
});

export default router;
