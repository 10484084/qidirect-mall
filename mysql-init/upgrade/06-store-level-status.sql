-- 档位支持冻结：冻结档位不可被前台选择，历史门店与订单不受影响
ALTER TABLE store_level
    ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=冻结' AFTER sort;
