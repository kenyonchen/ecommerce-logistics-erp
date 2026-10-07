-- 跨境大卖ERP 数据库初始化
-- 该脚本在 MySQL 容器首次启动时自动执行（/docker-entrypoint-initdb.d）。
-- 业务表结构由 Hibernate（ddl-auto: update）自动创建，这里只保证库与字符集就绪。

CREATE DATABASE IF NOT EXISTS `ecommerce_logistics_erp`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE `ecommerce_logistics_erp`;

-- 允许 root 从任意主机（含宿主机经端口映射连入，对 MySQL 是非 localhost 来源）登录，
-- 否则默认 root@localhost 只在容器内可连，宿主机 Spring Boot 连接会在握手阶段被断开，
-- 表现为 Communications link failure。与 docker-compose.yml 的 MYSQL_ROOT_HOST 互为兜底。
CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
