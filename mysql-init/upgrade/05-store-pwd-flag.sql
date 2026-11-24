-- 门店默认密码标记：后台新增门店统一初始密码 admin，首次登录强制修改
ALTER TABLE store
    ADD COLUMN pwd_need_change TINYINT NOT NULL DEFAULT 0 COMMENT '1=使用初始密码需强制修改' AFTER password;
