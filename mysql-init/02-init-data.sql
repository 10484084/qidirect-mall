-- 直销（加盟）商城一期 —— 初始化数据
-- 可重复执行（INSERT IGNORE）。默认管理员：admin / admin123（上线前必须修改）。

USE `qidirect_mall`;
SET NAMES utf8mb4;

-- 档位：1万 / 3万 / 5万；仅最高档（sort 最大）开启长期提成、业绩奖、买赠
INSERT IGNORE INTO `store_level` (`id`, `name`, `fee`, `sort`, `upgrade_days`, `long_term`, `has_bonus`, `has_gift`) VALUES
  (1, '1万档', 1000000, 1, 60, 0, 0, 0),
  (2, '3万档', 3000000, 2, 60, 0, 0, 0),
  (3, '5万档', 5000000, 3, 60, 1, 1, 1);

-- 默认超管
INSERT IGNORE INTO `admin_user` (`id`, `account`, `password`, `real_name`, `status`) VALUES
  (1, 'admin', '$2a$10$v5cMbd9RHJvYWTHxSzQejex2SE./6T4QX7sGfqdoAFeIYBqVNp/0e', '超级管理员', 1);

INSERT IGNORE INTO `admin_role` (`id`, `name`, `perms`) VALUES
  (1, '超级管理员', '["*"]');

INSERT IGNORE INTO `admin_user_role` (`admin_id`, `role_id`) VALUES
  (1, 1);

-- 默认业绩奖规则：50万→0.5%，70万→1%（全额累进，后台可改）
INSERT IGNORE INTO `performance_rule` (`id`, `min_amount`, `rate`, `sort`, `status`) VALUES
  (1, 50000000, 0.0050, 1, 1),
  (2, 70000000, 0.0100, 2, 1);

-- 默认加盟协议
INSERT IGNORE INTO `agreement` (`id`, `title`, `version`, `content`, `status`) VALUES
  (1, '加盟协议', 'v1.0',
   '一、门店自愿加盟并遵守厂家统一价格与经营规范。二、加盟费全额转为预存余额，仅可用于订货，不予退款。三、门店可发展下级门店，提成按平台规则执行。四、双方应遵守国家法律法规，本协议自签署之日起生效。',
   1);

-- 示例商品：1 箱 = 12 瓶；V1/V2/V3 对应 1万/3万/5万档
INSERT IGNORE INTO `product_category` (`id`, `name`, `sort`, `status`) VALUES
  (1, '示例分类', 1, 1);

INSERT IGNORE INTO `product` (`id`, `category_id`, `name`, `cover`, `images`, `description`, `v3_gift_enabled`, `recommend`, `status`, `sort`, `deleted`) VALUES
  (1, 1, '示例白酒 500ml', '', NULL, '用于联调的示例商品', 1, 1, 1, 1, 0);

INSERT IGNORE INTO `product_sku` (`id`, `product_id`, `spec_name`, `factor`, `sort`) VALUES
  (1, 1, '瓶', 1, 1),
  (2, 1, '箱', 12, 2);

INSERT IGNORE INTO `sku_level_price` (`sku_id`, `level_id`, `price`) VALUES
  (1, 1, 10000),
  (1, 2, 9500),
  (1, 3, 9000),
  (2, 1, 120000),
  (2, 2, 114000),
  (2, 3, 108000);

-- 示例买赠规则：满 12 瓶送 3 瓶（V3 买赠开关已开启）
INSERT IGNORE INTO `gift_rule` (`id`, `product_id`, `gift_sku_id`, `threshold_qty`, `gift_qty`, `enabled`, `start_time`, `end_time`) VALUES
  (1, 1, 1, 12, 3, 1, NULL, NULL);

-- 默认系统配置
INSERT IGNORE INTO `sys_config` (`cfg_key`, `cfg_value`, `remark`) VALUES
  ('commission_rate_1', '0.10', '一级提成比例'),
  ('commission_rate_2', '0.03', '二级提成比例'),
  ('withdraw_min_amount', '10000', '最低提现金额（分）'),
  ('withdraw_days', '15,last', '提现开放日，15=每月15日，last=月末'),
  ('order_timeout_minutes', '10', '订单支付超时（分钟）'),
  ('month_settle_day', '1', '业绩月结日'),
  ('service_wechat', '', '客服微信'),
  ('service_phone', '', '客服电话');
