/**
 * 后台权限点清单：code 为存储值，label 为界面展示名。
 * 新增权限点时在此登记，角色配置页会按 code 自动展示中文。
 */
export const PERM_GROUPS = [
  {
    title: '门店',
    items: [
      { code: 'store:list', label: '门店列表' },
      { code: 'store:detail', label: '门店详情' },
      { code: 'store:create', label: '新增门店' },
      { code: 'store:freeze', label: '冻结 / 解冻门店' }
    ]
  },
  {
    title: '商品',
    items: [
      { code: 'product:list', label: '商品档案' },
      { code: 'product:edit', label: '新增 / 编辑商品' },
      { code: 'category:manage', label: '商品分类' },
      { code: 'level:manage', label: '储值档位' }
    ]
  },
  {
    title: '交易',
    items: [
      { code: 'order:list', label: '订单查看' },
      { code: 'order:ship', label: '订单发货' },
      { code: 'order:cancel', label: '取消审核' },
      { code: 'purchase:collect', label: '线下补款' },
      { code: 'wallet:adjust', label: '余额调整' }
    ]
  },
  {
    title: '奖励',
    items: [
      { code: 'commission:view', label: '推荐奖励' },
      { code: 'performance:view', label: '团队奖励' }
    ]
  },
  {
    title: '提现',
    items: [
      { code: 'withdraw:audit', label: '提现审核' },
      { code: 'withdraw:batch', label: '打款批次' },
      { code: 'withdraw:export', label: '提现导出' }
    ]
  },
  {
    title: '财务',
    items: [
      { code: 'finance:wallet', label: '余额流水' },
      { code: 'finance:payment', label: '支付流水' },
      { code: 'finance:reconcile', label: '对账' },
      { code: 'finance:abnormal', label: '异常单处置' }
    ]
  },
  {
    title: '系统',
    items: [
      { code: 'admin:manage', label: '管理员' },
      { code: 'role:manage', label: '角色权限' },
      { code: 'system:config', label: '系统配置' },
      { code: 'log:view', label: '操作日志' }
    ]
  }
];

export const ALL_PERMS = PERM_GROUPS.flatMap((g) => g.items);

const LABEL_MAP = Object.fromEntries(ALL_PERMS.map((p) => [p.code, p.label]));

export function permLabel(code) {
  if (code === '*') return '全部权限';
  return LABEL_MAP[code] || code;
}
