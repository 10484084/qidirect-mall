-- 商品主单位：如 瓶/袋/盒，规格中的包装单位按「1 包装 = N 主单位」换算
ALTER TABLE product
    ADD COLUMN unit VARCHAR(16) NOT NULL DEFAULT '' COMMENT '主单位，如 瓶' AFTER name;
