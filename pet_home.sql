-- ============================================================
-- 宠享到家・宠物上门服务平台 数据库建表 SQL
-- 数据库名：pet_home
-- 版本：最终版
-- 数据库：MySQL 8.0+
-- 字符集：utf8mb4
-- 引擎：InnoDB
-- 说明：外键仅逻辑关联，不强制物理外键
-- ============================================================

CREATE DATABASE IF NOT EXISTS `pet_home` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `pet_home`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 模块一：用户与权限
-- ============================================================

-- ----------------------------
-- 1. 用户表
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid` VARCHAR(64) DEFAULT NULL COMMENT '微信openid',
  `name` VARCHAR(64) DEFAULT NULL COMMENT '姓名',
  `password` VARCHAR(100) DEFAULT NULL COMMENT '密码（BCrypt 密文），仅手机号注册的账号有值，微信登录用户为空',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `sex` TINYINT DEFAULT 0 COMMENT '性别 0未知 1男 2女',
  `id_number` VARCHAR(18) DEFAULT NULL COMMENT '身份证号',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`),
  -- 手机号唯一：手机号登录自动注册依赖该约束避免并发注册出重复账号
  -- （MySQL 唯一索引允许多个 NULL，微信登录用户手机号为空不受影响）
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ----------------------------
-- 2. 管理员表
-- ----------------------------
DROP TABLE IF EXISTS `employee`;
CREATE TABLE `employee` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `name` VARCHAR(50) NOT NULL COMMENT '姓名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码（加密）',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `sex` TINYINT DEFAULT 0 COMMENT '性别 0未知 1男 2女',
  `id_number` VARCHAR(18) DEFAULT NULL COMMENT '身份证号',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像',
  `status` TINYINT DEFAULT 1 COMMENT '状态 1启用 0禁用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

-- ============================================================
-- 模块二：地址与宠物档案
-- ============================================================

-- ----------------------------
-- 3. 地址簿表
-- ----------------------------
DROP TABLE IF EXISTS `address_book`;
CREATE TABLE `address_book` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `consignee` VARCHAR(50) NOT NULL COMMENT '联系人',
  `sex` TINYINT DEFAULT 0 COMMENT '性别 0未知 1男 2女',
  `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
  `province_code` VARCHAR(20) DEFAULT NULL COMMENT '省份编码',
  `province_name` VARCHAR(50) DEFAULT NULL COMMENT '省份名称',
  `city_code` VARCHAR(20) DEFAULT NULL COMMENT '城市编码',
  `city_name` VARCHAR(50) DEFAULT NULL COMMENT '城市名称',
  `district_code` VARCHAR(20) DEFAULT NULL COMMENT '区县编码',
  `district_name` VARCHAR(50) DEFAULT NULL COMMENT '区县名称',
  `detail` VARCHAR(255) NOT NULL COMMENT '详细地址',
  `label` VARCHAR(50) DEFAULT NULL COMMENT '标签',
  `is_default` TINYINT DEFAULT 0 COMMENT '是否默认 1是 0否',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地址簿表';

-- ----------------------------
-- 4. 宠物档案表
-- ----------------------------
DROP TABLE IF EXISTS `pet_profile`;
CREATE TABLE `pet_profile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `name` VARCHAR(50) NOT NULL COMMENT '宠物名',
  `breed` VARCHAR(50) DEFAULT NULL COMMENT '品种',
  `gender` TINYINT DEFAULT 0 COMMENT '性别 0未知 1公 2母',
  `birthday` DATE DEFAULT NULL COMMENT '生日',
  `weight` DECIMAL(5,2) DEFAULT NULL COMMENT '体重(kg)',
  `vaccine` VARCHAR(255) DEFAULT NULL COMMENT '疫苗情况',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_name` (`user_id`, `name`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宠物档案表';

-- ============================================================
-- 模块三：服务与美容师
-- ============================================================

-- ----------------------------
-- 5. 服务分类表
-- ----------------------------
DROP TABLE IF EXISTS `service_category`;
CREATE TABLE `service_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
  `sort` INT DEFAULT 0 COMMENT '排序',
  `status` TINYINT DEFAULT 1 COMMENT '状态 1启用 0禁用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务分类表';

-- ----------------------------
-- 6. 服务项目表
-- ----------------------------
DROP TABLE IF EXISTS `service_item`;
CREATE TABLE `service_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `category_id` BIGINT NOT NULL COMMENT '分类ID',
  `name` VARCHAR(100) NOT NULL COMMENT '服务名称',
  `price` DECIMAL(10,2) NOT NULL COMMENT '价格',
  `service_duration` INT DEFAULT NULL COMMENT '服务时长(分钟)',
  `pet_type` VARCHAR(20) DEFAULT NULL COMMENT '适配宠物类型 猫/狗/通用',
  `suit_weight` VARCHAR(50) DEFAULT NULL COMMENT '适配体重',
  `description` TEXT COMMENT '描述',
  `image` VARCHAR(255) DEFAULT NULL COMMENT '图片',
  `status` TINYINT DEFAULT 1 COMMENT '状态 1上架 0下架',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_name` (`category_id`, `name`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务项目表';

-- ----------------------------
-- 7. 美容师表
-- ----------------------------
DROP TABLE IF EXISTS `beautician`;
CREATE TABLE `beautician` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` VARCHAR(50) NOT NULL COMMENT '姓名',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像',
  `skill_tags` VARCHAR(255) DEFAULT NULL COMMENT '技能标签（逗号分隔）',
  `service_range` VARCHAR(255) DEFAULT NULL COMMENT '服务范围',
  `entry_time` DATE DEFAULT NULL COMMENT '入职时间',
  `score` DECIMAL(3,2) DEFAULT 5.00 COMMENT '评分',
  `status` TINYINT DEFAULT 1 COMMENT '状态 1在职 0离职',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='美容师表';

-- ----------------------------
-- 8. 美容师排班表
-- ----------------------------
DROP TABLE IF EXISTS `beautician_schedule`;
CREATE TABLE `beautician_schedule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `beautician_id` BIGINT NOT NULL COMMENT '美容师ID',
  `date` DATE NOT NULL COMMENT '日期',
  `time_slot` VARCHAR(50) NOT NULL COMMENT '时段，如14:00-16:00',
  `available` TINYINT DEFAULT 1 COMMENT '是否可接单 1可接单 0已占用',
  `order_id` BIGINT DEFAULT NULL COMMENT '占用订单ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_beautician_date_slot` (`beautician_id`, `date`, `time_slot`),
  KEY `idx_beautician_id` (`beautician_id`),
  KEY `idx_date` (`date`),
  KEY `idx_available` (`available`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='美容师排班表';

-- ============================================================
-- 模块四：预约与订单
-- ============================================================

-- ----------------------------
-- 9. 预约车表
-- ----------------------------
DROP TABLE IF EXISTS `reserve_cart`;
CREATE TABLE `reserve_cart` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `service_id` BIGINT NOT NULL COMMENT '服务项目ID',
  `quantity` INT DEFAULT 1 COMMENT '数量',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约车表';

-- ----------------------------
-- 10. 服务预约单表
-- ----------------------------
DROP TABLE IF EXISTS `service_order`;
CREATE TABLE `service_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_number` VARCHAR(50) NOT NULL COMMENT '订单号',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `address_id` BIGINT DEFAULT NULL COMMENT '地址ID',
  `pet_id` BIGINT DEFAULT NULL COMMENT '宠物ID',
  `beautician_id` BIGINT DEFAULT NULL COMMENT '美容师ID',
  `schedule_id` BIGINT DEFAULT NULL COMMENT '排班时段ID',
  `appointment_date` DATE DEFAULT NULL COMMENT '预约日期',
  `time_slot` VARCHAR(50) DEFAULT NULL COMMENT '预约时段',
  `total_amount` DECIMAL(10,2) NOT NULL COMMENT '总金额',
  `status` TINYINT DEFAULT 1 COMMENT '订单状态 1待支付 2待确认 3待上门 4服务中 5已完成 6已取消 7待评价',
  `message_id` VARCHAR(64) DEFAULT NULL COMMENT 'MQ延迟消息ID',
  `reminder_status` TINYINT DEFAULT 0 COMMENT '提醒状态 0未提醒 1已提醒 2已取消',
  `pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_number` (`order_number`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_beautician_id` (`beautician_id`),
  KEY `idx_status` (`status`),
  KEY `idx_message_id` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务预约单表';

-- ----------------------------
-- 11. 订单明细表
-- ----------------------------
DROP TABLE IF EXISTS `order_detail`;
CREATE TABLE `order_detail` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `service_id` BIGINT NOT NULL COMMENT '服务项目ID',
  `name` VARCHAR(100) NOT NULL COMMENT '服务名称（快照）',
  `price` DECIMAL(10,2) NOT NULL COMMENT '服务价格（快照）',
  `quantity` INT DEFAULT 1 COMMENT '数量',
  `amount` DECIMAL(10,2) NOT NULL COMMENT '小计金额',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- ----------------------------
-- 11-1. 订单评价表
-- ----------------------------
DROP TABLE IF EXISTS `order_comment`;
CREATE TABLE `order_comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `beautician_id` BIGINT DEFAULT NULL COMMENT '美容师ID',
  `score` TINYINT NOT NULL COMMENT '评分 1-5',
  `content` VARCHAR(500) DEFAULT NULL COMMENT '评价内容',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_beautician_id` (`beautician_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单评价表';

-- ============================================================
-- 模块五：AI 问答与知识库
-- ============================================================

-- ----------------------------
-- 12. 问答历史表
-- ----------------------------
DROP TABLE IF EXISTS `ai_conversation`;
CREATE TABLE `ai_conversation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `pet_id` BIGINT DEFAULT NULL COMMENT '关联宠物ID',
  `question` TEXT NOT NULL COMMENT '问题',
  `answer` TEXT NOT NULL COMMENT '答案',
  `references` TEXT COMMENT '参考来源JSON（docId/title/content 数组）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提问时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_pet_id` (`pet_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI问答历史表';

-- ----------------------------
-- 13. 知识库文档表
-- ----------------------------
DROP TABLE IF EXISTS `knowledge_doc`;
CREATE TABLE `knowledge_doc` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` VARCHAR(200) NOT NULL COMMENT '标题',
  `category` VARCHAR(50) DEFAULT NULL COMMENT '分类：护理/饮食/疾病预防',
  `content` TEXT COMMENT '内容',
  `enabled` TINYINT DEFAULT 1 COMMENT '是否启用 1启用 0禁用',
  `index_status` TINYINT DEFAULT 0 COMMENT '索引状态 0未索引 1索引中 2正常 3失败',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_title` (`title`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_category` (`category`),
  KEY `idx_index_status` (`index_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 初始化管理员账号
-- 用户名：admin
-- 密码：123456（BCrypt 加密存储，与 EmployeeServiceImpl 的 BCryptPasswordEncoder 校验保持一致）
-- ============================================================
INSERT INTO `employee` (`username`, `password`, `name`, `phone`, `sex`, `status`, `create_time`, `update_time`)
VALUES ('admin', '$2a$10$l1VUQ2bO.T167NU6Vb7zhOnYtaWA8qMxiPnACixVJvQYTOi.bqohe', '超级管理员', '13800000000', 1, 1, NOW(), NOW());

-- ============================================================
-- 初始化演示用户（H5 用户端登录页底部提示的演示账号）
-- 手机号：13800138000
-- 密码：123456（BCrypt 加密存储，与 UserServiceImpl 的 BCryptPasswordEncoder 校验保持一致）
-- openid 留空表示该账号未绑定微信，仅供手机号+密码登录
-- ============================================================
INSERT INTO `user` (`openid`, `name`, `password`, `phone`, `sex`, `create_time`, `update_time`)
VALUES (NULL, '演示用户', '$2a$10$zq2JYyKkyRhC9B.uV3PMl.kQagEmoxUETvtbiL2i2DWLD8EeapIOi', '13800138000', 0, NOW(), NOW());

-- ============================================================
-- 说明：
-- 1. 所有外键均为逻辑关联，未添加 FOREIGN KEY 约束。
-- 2. 所有业务表均包含 create_time 和 update_time。
-- 3. 知识库文档表 knowledge_doc 保留，管理端通过数据库维护，
--    RAG 服务查询时以 enabled=1 且 index_status=2 为白名单过滤。
-- ============================================================