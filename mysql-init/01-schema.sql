-- 直销（加盟）商城一期 —— 全量表结构
-- 依据《直销商城-技术设计文档 v2.9》第三章，并落实 spec.md「口径裁定」。
-- 可重复执行；已存在表不会被重建。

CREATE DATABASE IF NOT EXISTS `qidirect_mall` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `qidirect_mall`;
SET NAMES utf8mb4;

-- ============ 门店与等级 ============

CREATE TABLE IF NOT EXISTS `store` (
  `id`                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `parent_id`         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上级，0=厂家',
  `path`              VARCHAR(500) NOT NULL DEFAULT '' COMMENT '物化路径 /1/5/12/',
  `level_id`          BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `level_paid_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '当前等级已付对价（分），等级变更置值不累加',
  `name`              VARCHAR(64)  NOT NULL DEFAULT '',
  `phone`             VARCHAR(20)  NOT NULL,
  `username`          VARCHAR(32)  NULL COMMENT '登录用户名，默认取手机号',
  `password`          VARCHAR(100) NULL COMMENT 'BCrypt 密文，NULL=未设置',
  `nickname`          VARCHAR(64)  NOT NULL DEFAULT '',
  `avatar`            VARCHAR(255) NOT NULL DEFAULT '',
  `join_time`         DATETIME     NULL,
  `upgrade_deadline`  DATETIME     NULL,
  `invite_code`       VARCHAR(16)  NOT NULL,
  `wx_openid`         VARCHAR(64)  NOT NULL DEFAULT '',
  `status`            TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 2冻结',
  `real_name`         VARCHAR(32)  NOT NULL DEFAULT '',
  `id_card`           VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'AES 加密',
  `id_card_hash`      CHAR(64)     NOT NULL DEFAULT '' COMMENT 'SHA256(身份证+盐)',
  `license_no`        VARCHAR(64)  NOT NULL DEFAULT '',
  `license_no_hash`   CHAR(64)     NOT NULL DEFAULT '' COMMENT 'SHA256(执照号+盐)',
  `biz_images`        JSON         NULL,
  `auth_status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0未填写 1已填写 2已核验',
  `auth_provider`     VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '预留 aliyun',
  `auth_trade_no`     VARCHAR(64)  NOT NULL DEFAULT '',
  `duplicate_flag`    TINYINT      NOT NULL DEFAULT 0,
  `duplicate_of`      BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `bank_holder`       VARCHAR(32)  NOT NULL DEFAULT '',
  `bank_name`         VARCHAR(64)  NOT NULL DEFAULT '',
  `bank_account`      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT 'AES 加密',
  `bank_holder_approved`   TINYINT NOT NULL DEFAULT 0 COMMENT '持卡人特批 1是 0否',
  `bank_holder_approve_by` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `bank_holder_approve_at` DATETIME NULL,
  `bank_holder_proof`      JSON NULL COMMENT '特批凭证',
  `remark`            VARCHAR(255) NOT NULL DEFAULT '',
  `version`           INT          NOT NULL DEFAULT 0,
  `deleted`           TINYINT      NOT NULL DEFAULT 0,
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_phone` (`phone`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_invite` (`invite_code`),
  KEY `idx_parent` (`parent_id`),
  KEY `idx_path` (`path`),
  KEY `idx_status` (`status`),
  KEY `idx_idcard` (`id_card_hash`),
  KEY `idx_license` (`license_no_hash`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '门店';

CREATE TABLE IF NOT EXISTS `store_level_log` (
  `id`         BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `store_id`   BIGINT UNSIGNED NOT NULL,
  `from_level` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `to_level`   BIGINT UNSIGNED NOT NULL,
  `biz_type`   VARCHAR(16) NOT NULL COMMENT 'JOIN/UPGRADE/RECHARGE',
  `biz_no`     VARCHAR(32) NOT NULL DEFAULT '',
  `amount`     BIGINT NOT NULL DEFAULT 0 COMMENT '本次实付',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_store` (`store_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '档位变更流水';

CREATE TABLE IF NOT EXISTS `store_level` (
  `id`           BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `name`         VARCHAR(32) NOT NULL,
  `fee`          BIGINT      NOT NULL COMMENT '加盟费（分）',
  `sort`         INT         NOT NULL DEFAULT 0,
  `upgrade_days` INT         NOT NULL DEFAULT 60,
  `long_term`    TINYINT     NOT NULL DEFAULT 0 COMMENT '享长期提成',
  `has_bonus`    TINYINT     NOT NULL DEFAULT 0 COMMENT '享团队业绩奖',
  `has_gift`     TINYINT     NOT NULL DEFAULT 0 COMMENT '享订货买赠',
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_sort` (`sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '档位';

-- ============ 配置 ============

CREATE TABLE IF NOT EXISTS `sys_config` (
  `id`         BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `cfg_key`    VARCHAR(64)  NOT NULL,
  `cfg_value`  VARCHAR(512) NOT NULL,
  `remark`     VARCHAR(128) NOT NULL DEFAULT '',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_key` (`cfg_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统配置';

-- ============ 钱包 ============

CREATE TABLE IF NOT EXISTS `wallet` (
  `store_id`           BIGINT UNSIGNED PRIMARY KEY,
  `deposit_balance`    BIGINT NOT NULL DEFAULT 0 COMMENT '预存（只能订货）',
  `commission_balance` BIGINT NOT NULL DEFAULT 0 COMMENT '佣金（只能提现）',
  `frozen_amount`      BIGINT NOT NULL DEFAULT 0 COMMENT '提现冻结',
  `version`            INT    NOT NULL DEFAULT 0,
  `updated_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '门店钱包';

CREATE TABLE IF NOT EXISTS `wallet_log` (
  `id`            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `store_id`      BIGINT UNSIGNED NOT NULL,
  `account`       TINYINT NOT NULL COMMENT '1预存 2佣金',
  `biz_type`      VARCHAR(32) NOT NULL COMMENT 'JOIN/UPGRADE/RECHARGE/ORDER_BALANCE/ORDER_ONLINE/ORDER_REFUND/COMMISSION/BONUS/BONUS_REVERSAL/WITHDRAW/OFFLINE/ADJUST',
  `direction`     TINYINT NOT NULL COMMENT '1入账 -1出账',
  `amount`        BIGINT  NOT NULL,
  `balance_after` BIGINT  NOT NULL,
  `biz_no`        VARCHAR(64) NOT NULL DEFAULT '',
  `source`        TINYINT NOT NULL DEFAULT 1 COMMENT '1线上 2线下 3人工',
  `proof_images`  JSON NULL,
  `operator_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `remark`        VARCHAR(255) NOT NULL DEFAULT '',
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_store_time` (`store_id`, `created_at`),
  KEY `idx_biz` (`biz_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '余额流水';

-- ============ 商品 ============

CREATE TABLE IF NOT EXISTS `product_category` (
  `id`         BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `name`       VARCHAR(32) NOT NULL,
  `sort`       INT NOT NULL DEFAULT 0,
  `status`     TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品分类（技术文档遗漏，按接口补齐）';

CREATE TABLE IF NOT EXISTS `product` (
  `id`              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `category_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `name`            VARCHAR(128) NOT NULL,
  `cover`           VARCHAR(255) NOT NULL DEFAULT '',
  `images`          JSON NULL,
  `description`     TEXT NULL,
  `v3_gift_enabled` TINYINT NOT NULL DEFAULT 0,
  `recommend`       TINYINT NOT NULL DEFAULT 0,
  `status`          TINYINT NOT NULL DEFAULT 1,
  `sort`            INT NOT NULL DEFAULT 0,
  `deleted`         TINYINT NOT NULL DEFAULT 0,
  `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品';

CREATE TABLE IF NOT EXISTS `product_sku` (
  `id`         BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `product_id` BIGINT UNSIGNED NOT NULL,
  `spec_name`  VARCHAR(32) NOT NULL COMMENT '瓶/箱',
  `factor`     INT NOT NULL DEFAULT 1 COMMENT '折算瓶数',
  `sort`       INT NOT NULL DEFAULT 0,
  KEY `idx_product` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '规格';

CREATE TABLE IF NOT EXISTS `sku_level_price` (
  `id`       BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `sku_id`   BIGINT UNSIGNED NOT NULL,
  `level_id` BIGINT UNSIGNED NOT NULL,
  `price`    BIGINT NOT NULL COMMENT '分',
  UNIQUE KEY `uk_sku_level` (`sku_id`, `level_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '规格档位价（加档位只加数据）';

CREATE TABLE IF NOT EXISTS `gift_rule` (
  `id`            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `product_id`    BIGINT UNSIGNED NOT NULL,
  `gift_sku_id`   BIGINT UNSIGNED NOT NULL COMMENT '赠品落在最小规格（瓶）的 sku',
  `threshold_qty` INT NOT NULL COMMENT '门槛（按瓶折算）',
  `gift_qty`      INT NOT NULL COMMENT '赠送瓶数',
  `enabled`       TINYINT NOT NULL DEFAULT 1,
  `enabled_product` BIGINT UNSIGNED GENERATED ALWAYS AS (IF(`enabled` = 1, `product_id`, NULL)) STORED COMMENT '用于唯一约束',
  `start_time`    DATETIME NULL,
  `end_time`      DATETIME NULL,
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_product` (`product_id`),
  UNIQUE KEY `uk_enabled_product` (`enabled_product`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '买赠规则（同商品仅一条 enabled=1）';

-- ============ 业务单据 ============

CREATE TABLE IF NOT EXISTS `join_order` (
  `id`         BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `order_no`   VARCHAR(32) NOT NULL,
  `store_id`   BIGINT UNSIGNED NOT NULL,
  `level_id`   BIGINT UNSIGNED NOT NULL,
  `type`       TINYINT NOT NULL COMMENT '1新加盟 2补差升级（超期购买档位走 recharge_order）',
  `before_level_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '变更前档位',
  `amount`     BIGINT NOT NULL,
  `status`     TINYINT NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已取消 3异常 4已退款',
  `resolve_type` TINYINT NOT NULL DEFAULT 0 COMMENT '1补开通 2原路退款 3仅关闭',
  `resolve_by`   BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `resolve_at`   DATETIME NULL,
  `pay_time`   DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `active_mark` BIGINT UNSIGNED GENERATED ALWAYS AS (IF(`status` IN (0,3), `store_id`, NULL)) STORED COMMENT '仅未结单（待支付/异常）占位',
  UNIQUE KEY `uk_active` (`active_mark`),
  UNIQUE KEY `uk_no` (`order_no`),
  KEY `idx_store` (`store_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '加盟/升级单';

CREATE TABLE IF NOT EXISTS `recharge_activity` (
  `id`              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `title`           VARCHAR(64) NOT NULL,
  `limit_per_store` INT NOT NULL DEFAULT 0,
  `start_time`      DATETIME NULL,
  `end_time`        DATETIME NULL,
  `status`          TINYINT NOT NULL DEFAULT 1,
  `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '预存活动';

CREATE TABLE IF NOT EXISTS `recharge_activity_tier` (
  `id`          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `activity_id` BIGINT UNSIGNED NOT NULL,
  `level_id`    BIGINT UNSIGNED NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '活动档位';

CREATE TABLE IF NOT EXISTS `recharge_order` (
  `id`          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `order_no`    VARCHAR(32) NOT NULL,
  `store_id`    BIGINT UNSIGNED NOT NULL,
  `activity_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `level_id`    BIGINT UNSIGNED NOT NULL,
  `amount`      BIGINT NOT NULL,
  `status`      TINYINT NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已取消 3异常 4已退款',
  `pay_time`    DATETIME NULL,
  `resolve_type` TINYINT NOT NULL DEFAULT 0,
  `resolve_by`   BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `resolve_at`   DATETIME NULL,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `active_mark` BIGINT UNSIGNED GENERATED ALWAYS AS (IF(`status` IN (0,3), `store_id`, NULL)) STORED COMMENT '仅未结单（待支付/异常）占位',
  UNIQUE KEY `uk_active` (`active_mark`),
  UNIQUE KEY `uk_no` (`order_no`),
  KEY `idx_store` (`store_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '预存单';

CREATE TABLE IF NOT EXISTS `orders` (
  `id`             BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `order_no`       VARCHAR(32) NOT NULL,
  `store_id`       BIGINT UNSIGNED NOT NULL,
  `total_amount`   BIGINT NOT NULL,
  `deposit_amount` BIGINT NOT NULL DEFAULT 0,
  `online_amount`  BIGINT NOT NULL DEFAULT 0,
  `status`         TINYINT NOT NULL DEFAULT 0 COMMENT '0待支付 1待发货 2已发货 3已完成 4已取消',
  `receiver_name`  VARCHAR(32) NOT NULL DEFAULT '',
  `receiver_phone` VARCHAR(20) NOT NULL DEFAULT '',
  `receiver_addr`  VARCHAR(255) NOT NULL DEFAULT '',
  `ship_type`      TINYINT NOT NULL DEFAULT 0 COMMENT '1物流 2其他',
  `ship_company`   VARCHAR(64) NOT NULL DEFAULT '',
  `ship_no`        VARCHAR(64) NOT NULL DEFAULT '',
  `ship_images`    JSON NULL,
  `ship_remark`    VARCHAR(255) NOT NULL DEFAULT '',
  `pay_time`       DATETIME NULL,
  `ship_time`      DATETIME NULL,
  `finish_time`    DATETIME NULL,
  `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_no` (`order_no`),
  KEY `idx_store` (`store_id`, `created_at`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单';

CREATE TABLE IF NOT EXISTS `order_item` (
  `id`         BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `order_id`   BIGINT UNSIGNED NOT NULL,
  `product_id` BIGINT UNSIGNED NOT NULL,
  `sku_id`     BIGINT UNSIGNED NOT NULL,
  `spec_name`  VARCHAR(32) NOT NULL,
  `price`      BIGINT NOT NULL,
  `qty`        INT NOT NULL,
  `is_gift`    TINYINT NOT NULL DEFAULT 0,
  KEY `idx_order` (`order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单明细';

CREATE TABLE IF NOT EXISTS `cart_item` (
  `id`         BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `store_id`   BIGINT UNSIGNED NOT NULL,
  `product_id` BIGINT UNSIGNED NOT NULL,
  `sku_id`     BIGINT UNSIGNED NOT NULL,
  `qty`        INT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_store_sku` (`store_id`, `sku_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '购物车';

-- ============ 支付 ============

CREATE TABLE IF NOT EXISTS `payment` (
  `id`           BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `pay_no`       VARCHAR(32) NOT NULL COMMENT '即 terminal_trace',
  `biz_type`     VARCHAR(16) NOT NULL COMMENT 'JOIN/UPGRADE/RECHARGE/ORDER',
  `biz_id`       BIGINT UNSIGNED NOT NULL,
  `store_id`     BIGINT UNSIGNED NOT NULL,
  `amount`       BIGINT NOT NULL,
  `channel`      TINYINT NOT NULL COMMENT '1公众号JSAPI 2聚合码(预留) 3线下收款',
  `offline_method` TINYINT NOT NULL DEFAULT 0 COMMENT '线下收款方式：1现金 2转账 3刷卡',
  `status`       TINYINT NOT NULL DEFAULT 0 COMMENT '0待支付 1支付中 2成功 3失败 4关闭 5已退款',
  `out_trade_no` VARCHAR(64) NOT NULL DEFAULT '',
  `paid_at`      DATETIME NULL,
  `refund_at`    DATETIME NULL,
  `refund_amount` BIGINT NOT NULL DEFAULT 0,
  `refund_no`    VARCHAR(64) NOT NULL DEFAULT '',
  `refund_by`    BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `expire_at`    DATETIME NULL,
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_pay_no` (`pay_no`),
  `success_mark` TINYINT GENERATED ALWAYS AS (IF(`status` = 2, 1, NULL)) STORED,
  UNIQUE KEY `uk_biz_success` (`biz_type`, `biz_id`, `success_mark`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_status` (`status`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '支付单';

CREATE TABLE IF NOT EXISTS `payment_notify_log` (
  `id`         BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `pay_no`     VARCHAR(32) NOT NULL,
  `raw_body`   TEXT NOT NULL,
  `verified`   TINYINT NOT NULL DEFAULT 0,
  `handled`    TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_pay_no` (`pay_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '回调日志';

-- ============ 提成与业绩 ============

CREATE TABLE IF NOT EXISTS `commission_record` (
  `id`            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `biz_type`      VARCHAR(16) NOT NULL COMMENT 'JOIN/UPGRADE/RECHARGE/ORDER/OFFLINE',
  `biz_no`        VARCHAR(32) NOT NULL,
  `from_store_id` BIGINT UNSIGNED NOT NULL,
  `to_store_id`   BIGINT UNSIGNED NOT NULL,
  `level`         TINYINT NOT NULL COMMENT '1一级 2二级',
  `rate`          DECIMAL(6,4) NOT NULL,
  `base_amount`   BIGINT NOT NULL,
  `amount`        BIGINT NOT NULL,
  `status`        TINYINT NOT NULL DEFAULT 1 COMMENT '1已入账 2已冲正',
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_biz_store` (`biz_type`, `biz_no`, `to_store_id`),
  KEY `idx_to` (`to_store_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '提成记录';

CREATE TABLE IF NOT EXISTS `performance_record` (
  `id`         BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `store_id`   BIGINT UNSIGNED NOT NULL COMMENT '付款门店',
  `owner_path` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '支付时刻付款门店自身的 path 快照（含自己）',
  `month`      CHAR(7) NOT NULL,
  `amount`     BIGINT NOT NULL,
  `biz_type`   VARCHAR(16) NOT NULL COMMENT 'JOIN/UPGRADE/RECHARGE/ORDER/OFFLINE/ADJUST',
  `biz_no`     VARCHAR(32) NOT NULL,
  `source`     TINYINT NOT NULL DEFAULT 1 COMMENT '1系统 2人工',
  `operator_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `remark`     VARCHAR(255) NOT NULL DEFAULT '',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_biz` (`biz_type`, `biz_no`),
  KEY `idx_store_month` (`store_id`, `month`),
  KEY `idx_month` (`month`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '业绩流水';

CREATE TABLE IF NOT EXISTS `performance_rule` (
  `id`         BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `min_amount` BIGINT NOT NULL,
  `rate`       DECIMAL(6,4) NOT NULL,
  `sort`       INT NOT NULL DEFAULT 0,
  `status`     TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '业绩奖规则';

CREATE TABLE IF NOT EXISTS `performance_bonus` (
  `id`               BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `store_id`         BIGINT UNSIGNED NOT NULL,
  `month`            CHAR(7) NOT NULL,
  `performance`      BIGINT NOT NULL COMMENT '结算快照（分），不可直接编辑',
  `rule_id`          BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `rate`             DECIMAL(6,4) NOT NULL DEFAULT 0,
  `bonus_amount`     BIGINT NOT NULL DEFAULT 0,
  `status`           TINYINT NOT NULL DEFAULT 0 COMMENT '0待生成 1已入账 2已冲正',
  `path_snapshot`    VARCHAR(500) NOT NULL DEFAULT '',
  `subtree_snapshot` JSON NULL,
  `revision_of`      BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `settled_at`       DATETIME NULL,
  `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_store_month_rev` (`store_id`, `month`, `revision_of`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '业绩奖记录（revision_of=0 为原始记录）';

-- ============ 提现 ============

CREATE TABLE IF NOT EXISTS `withdraw` (
  `id`            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `store_id`      BIGINT UNSIGNED NOT NULL,
  `amount`        BIGINT NOT NULL,
  `bank_holder`   VARCHAR(32) NOT NULL,
  `bank_name`     VARCHAR(64) NOT NULL,
  `bank_account`  VARCHAR(64) NOT NULL,
  `status`        TINYINT NOT NULL DEFAULT 0 COMMENT '0待审核 1待打款 2已打款 3已驳回 4打款失败',
  `fail_reason`   VARCHAR(255) NOT NULL DEFAULT '',
  `fail_at`       DATETIME NULL,
  `fail_by`       BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `batch_id`      BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `reject_reason` VARCHAR(255) NOT NULL DEFAULT '',
  `proof_images`  JSON NULL,
  `apply_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `audit_time`    DATETIME NULL,
  `paid_time`     DATETIME NULL,
  `audit_admin`   BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `version`       INT NOT NULL DEFAULT 0,
  KEY `idx_store` (`store_id`),
  KEY `idx_status` (`status`, `apply_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '提现申请';

CREATE TABLE IF NOT EXISTS `withdraw_batch` (
  `id`           BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `batch_no`     VARCHAR(32) NOT NULL,
  `total_amount` BIGINT NOT NULL,
  `count`        INT NOT NULL,
  `fail_count`   INT NOT NULL DEFAULT 0,
  `fail_amount`  BIGINT NOT NULL DEFAULT 0,
  `status`       TINYINT NOT NULL DEFAULT 0,
  `exported_at`  DATETIME NULL,
  `paid_at`      DATETIME NULL,
  `proof_images` JSON NULL,
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_no` (`batch_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '打款批次';

-- ============ 后台与留痕 ============

CREATE TABLE IF NOT EXISTS `admin_user` (
  `id`         BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `account`    VARCHAR(32) NOT NULL,
  `password`   VARCHAR(100) NOT NULL,
  `real_name`  VARCHAR(32) NOT NULL DEFAULT '',
  `status`     TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_account` (`account`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员';

CREATE TABLE IF NOT EXISTS `admin_role` (
  `id`    BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `name`  VARCHAR(32) NOT NULL,
  `perms` JSON NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色';

CREATE TABLE IF NOT EXISTS `admin_user_role` (
  `admin_id` BIGINT UNSIGNED NOT NULL,
  `role_id`  BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (`admin_id`, `role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员角色';

CREATE TABLE IF NOT EXISTS `agreement` (
  `id`         BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `title`      VARCHAR(64) NOT NULL,
  `version`    VARCHAR(16) NOT NULL,
  `content`    MEDIUMTEXT NOT NULL,
  `status`     TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_version` (`version`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '协议正文与版本';

CREATE TABLE IF NOT EXISTS `store_parent_change` (
  `id`            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `store_id`      BIGINT UNSIGNED NOT NULL,
  `old_parent_id` BIGINT UNSIGNED NOT NULL,
  `new_parent_id` BIGINT UNSIGNED NOT NULL,
  `old_path`      VARCHAR(500) NOT NULL DEFAULT '',
  `new_path`      VARCHAR(500) NOT NULL DEFAULT '',
  `reason`        VARCHAR(255) NOT NULL DEFAULT '',
  `status`        TINYINT NOT NULL DEFAULT 0 COMMENT '0待审批 1已通过 2已驳回',
  `applicant`     VARCHAR(32) NOT NULL DEFAULT '',
  `approver`      VARCHAR(32) NOT NULL DEFAULT '',
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_store` (`store_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '改上级审批记录';

CREATE TABLE IF NOT EXISTS `notice` (
  `id`          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `target_type` TINYINT NOT NULL COMMENT '1门店 2管理员 3全体',
  `target_id`   BIGINT UNSIGNED NOT NULL,
  `title`       VARCHAR(64) NOT NULL,
  `content`     VARCHAR(512) NOT NULL DEFAULT '',
  `biz_type`    VARCHAR(32) NOT NULL DEFAULT '',
  `biz_no`      VARCHAR(64) NOT NULL DEFAULT '',
  `readed`      TINYINT NOT NULL DEFAULT 0,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_target` (`target_type`, `target_id`, `readed`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内消息';

CREATE TABLE IF NOT EXISTS `sms_template` (
  `id`          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `code`        VARCHAR(32) NOT NULL,
  `provider_id` VARCHAR(64) NOT NULL DEFAULT '',
  `content`     VARCHAR(512) NOT NULL,
  `status`      TINYINT NOT NULL DEFAULT 1,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '短信模板';

CREATE TABLE IF NOT EXISTS `store_agreement` (
  `id`           BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `store_id`     BIGINT UNSIGNED NOT NULL,
  `agreement_id` BIGINT UNSIGNED NOT NULL,
  `version`      VARCHAR(16) NOT NULL,
  `sign_type`    TINYINT NOT NULL DEFAULT 1 COMMENT '1线上勾选 2纸质上传',
  `proof_images` JSON NULL COMMENT '纸质协议扫描件',
  `signed_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `ip`           VARCHAR(45) NOT NULL DEFAULT '',
  KEY `idx_store` (`store_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '协议签署留痕';

CREATE TABLE IF NOT EXISTS `store_event` (
  `id`         BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `store_id`   BIGINT UNSIGNED NOT NULL COMMENT '事件归属门店',
  `event_type` VARCHAR(24) NOT NULL COMMENT 'JOIN/UPGRADE/RECHARGE/ORDER/ORDER_PAID/ORDER_CANCEL/SHIP/ORDER_FINISH/COMMISSION/BONUS/BONUS_REVERSAL/WITHDRAW_APPLY/WITHDRAW_AUDIT/WITHDRAW_PAID/WITHDRAW_FAIL/OFFLINE/ADJUST/LEVEL_CHANGE/FREEZE/UNFREEZE/PARENT_CHANGE/PAY_REFUND',
  `biz_no`     VARCHAR(64) NULL COMMENT '业务单号，无单号事件写 NULL（唯一索引不约束 NULL）',
  `title`      VARCHAR(128) NOT NULL COMMENT '一句话描述',
  `amount`     BIGINT NOT NULL DEFAULT 0 COMMENT '涉及金额（分）',
  `direction`  TINYINT NOT NULL DEFAULT 0 COMMENT '1入账 -1出账 0无关',
  `account`    TINYINT NOT NULL DEFAULT 0 COMMENT '1预存 2佣金 0无关',
  `detail`     JSON NULL COMMENT '关键字段快照',
  `proof_images` JSON NULL COMMENT '凭证图片',
  `operator_type` TINYINT NOT NULL DEFAULT 1 COMMENT '1门店 2管理员 3系统',
  `operator_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_store_time` (`store_id`, `created_at`),
  KEY `idx_type` (`store_id`, `event_type`, `created_at`),
  `dedup_mark` VARCHAR(128) GENERATED ALWAYS AS (
      IF(`event_type` IN ('JOIN','COMMISSION','BONUS','BONUS_REVERSAL','ORDER_CANCEL'),
         CONCAT(`event_type`, ':', `biz_no`, ':', `store_id`), NULL)) STORED,
  UNIQUE KEY `uk_dedup` (`dedup_mark`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '门店事件流水（全量留痕）';

CREATE TABLE IF NOT EXISTS `operation_log` (
  `id`         BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  `operator`   VARCHAR(32) NOT NULL,
  `module`     VARCHAR(32) NOT NULL,
  `action`     VARCHAR(64) NOT NULL,
  `target`     VARCHAR(64) NOT NULL DEFAULT '',
  `detail`     JSON NULL,
  `ip`         VARCHAR(45) NOT NULL DEFAULT '',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_time` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志';
