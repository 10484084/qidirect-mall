-- Upgrade 03: store username/password login.
-- Only for databases created before this change; fresh installs already
-- contain these columns via 01-schema.sql. Run manually:
--   docker exec -i <mysql-container> sh -c \
--     'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 qidirect_mall' \
--     < mysql-init/upgrade/03-store-login.sql
-- Note: keep comments in ASCII to avoid 1064 errors with default client charset.

ALTER TABLE `store`
  ADD COLUMN `username` VARCHAR(32) NULL AFTER `phone`,
  ADD COLUMN `password` VARCHAR(100) NULL AFTER `username`;

UPDATE `store` SET `username` = `phone` WHERE `username` IS NULL OR `username` = '';

ALTER TABLE `store` ADD UNIQUE KEY `uk_username` (`username`);
