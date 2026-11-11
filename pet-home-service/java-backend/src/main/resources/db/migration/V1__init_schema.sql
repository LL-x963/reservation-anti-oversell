-- ============================================================
-- V1：宠物上门服务平台基础表结构
--
-- 约定：
-- 1. 不使用 DROP TABLE / TRUNCATE，迁移脚本对已有数据必须是安全的；
-- 2. 外键均为逻辑关联，不建立物理外键（便于分库与数据迁移）；
-- 3. 所有业务表包含 create_time / update_time；
-- 4. 索引按查询路径建立复合索引，避免全表扫描。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 用户
-- ------------------------------------------------------------
CREATE TABLE `user`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `openid`      VARCHAR(64)           DEFAULT NULL COMMENT '微信openid',
    `name`        VARCHAR(64)           DEFAULT NULL COMMENT '姓名',
    `password`    VARCHAR(100)          DEFAULT NULL COMMENT 'BCrypt 密码，微信登录用户为空',
    `phone`       VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
    `sex`         TINYINT               DEFAULT 0 COMMENT '性别 0未知 1男 2女',
    `id_number`   VARCHAR(18)           DEFAULT NULL COMMENT '身份证号',
    `avatar`      VARCHAR(255)          DEFAULT NULL COMMENT '头像',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_openid` (`openid`),
    -- 手机号唯一：并发注册时由唯一索引兜底（MySQL 唯一索引允许多个 NULL）
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

-- ------------------------------------------------------------
-- 管理端员工
-- ------------------------------------------------------------
CREATE TABLE `employee`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(50) NOT NULL COMMENT '用户名',
    `name`        VARCHAR(50) NOT NULL COMMENT '姓名',
    `password`    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码',
    `phone`       VARCHAR(20)          DEFAULT NULL COMMENT '手机号',
    `sex`         TINYINT              DEFAULT 0 COMMENT '性别 0未知 1男 2女',
    `id_number`   VARCHAR(18)          DEFAULT NULL COMMENT '身份证号',
    `avatar`      VARCHAR(255)         DEFAULT NULL COMMENT '头像',
    `status`      TINYINT              DEFAULT 1 COMMENT '状态 1启用 0禁用',
    -- 默认最小权限：新增员工需显式授权为 ADMIN
    `role`        VARCHAR(32) NOT NULL DEFAULT 'OPERATOR' COMMENT '角色 ADMIN/OPERATOR',
    `create_time` DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='管理员表';

-- ------------------------------------------------------------
-- 地址簿
-- ------------------------------------------------------------
CREATE TABLE `address_book`
(
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT      NOT NULL COMMENT '用户ID',
    `consignee`     VARCHAR(50) NOT NULL COMMENT '联系人',
    `sex`           TINYINT              DEFAULT 0 COMMENT '性别 0未知 1男 2女',
    `phone`         VARCHAR(20) NOT NULL COMMENT '手机号',
    `province_code` VARCHAR(20)          DEFAULT NULL COMMENT '省份编码',
    `province_name` VARCHAR(50)          DEFAULT NULL COMMENT '省份名称',
    `city_code`     VARCHAR(20)          DEFAULT NULL COMMENT '城市编码',
    `city_name`     VARCHAR(50)          DEFAULT NULL COMMENT '城市名称',
    `district_code` VARCHAR(20)          DEFAULT NULL COMMENT '区县编码',
    `district_name` VARCHAR(50)          DEFAULT NULL COMMENT '区县名称',
    `detail`        VARCHAR(255) NOT NULL COMMENT '详细地址',
    `label`         VARCHAR(50)          DEFAULT NULL COMMENT '标签',
    `is_default`    TINYINT              DEFAULT 0 COMMENT '是否默认 1是 0否',
    `create_time`   DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='地址簿表';

-- ------------------------------------------------------------
-- 宠物档案
-- ------------------------------------------------------------
CREATE TABLE `pet_profile`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT      NOT NULL COMMENT '用户ID',
    `name`        VARCHAR(50) NOT NULL COMMENT '宠物名',
    `breed`       VARCHAR(50)          DEFAULT NULL COMMENT '品种',
    `gender`      TINYINT              DEFAULT 0 COMMENT '性别 0未知 1公 2母',
    `birthday`    DATE                 DEFAULT NULL COMMENT '生日',
    `weight`      DECIMAL(5, 2)        DEFAULT NULL COMMENT '体重(kg)',
    `vaccine`     VARCHAR(255)         DEFAULT NULL COMMENT '疫苗情况',
    `remark`      VARCHAR(255)         DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_name` (`user_id`, `name`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='宠物档案表';

-- ------------------------------------------------------------
-- 服务分类
-- ------------------------------------------------------------
CREATE TABLE `service_category`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(50) NOT NULL COMMENT '分类名称',
    `sort`        INT                  DEFAULT 0 COMMENT '排序',
    `status`      TINYINT              DEFAULT 1 COMMENT '状态 1启用 0禁用',
    `create_time` DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='服务分类表';

-- ------------------------------------------------------------
-- 服务项目
-- ------------------------------------------------------------
CREATE TABLE `service_item`
(
    `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `category_id`      BIGINT         NOT NULL COMMENT '分类ID',
    `name`             VARCHAR(100)   NOT NULL COMMENT '服务名称',
    `price`            DECIMAL(10, 2) NOT NULL COMMENT '价格',
    `service_duration` INT                     DEFAULT NULL COMMENT '服务时长(分钟)',
    `pet_type`         VARCHAR(20)             DEFAULT NULL COMMENT '适配宠物类型 猫/狗/通用',
    `suit_weight`      VARCHAR(50)             DEFAULT NULL COMMENT '适配体重',
    `description`      TEXT COMMENT '描述',
    `image`            VARCHAR(255)            DEFAULT NULL COMMENT '图片',
    `status`           TINYINT                 DEFAULT 1 COMMENT '状态 1上架 0下架',
    `create_time`      DATETIME                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_name` (`category_id`, `name`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='服务项目表';

-- ------------------------------------------------------------
-- 美容师
-- ------------------------------------------------------------
CREATE TABLE `beautician`
(
    `id`            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`          VARCHAR(50) NOT NULL COMMENT '姓名',
    `avatar`        VARCHAR(255)         DEFAULT NULL COMMENT '头像',
    `skill_tags`    VARCHAR(255)         DEFAULT NULL COMMENT '技能标签（逗号分隔）',
    `service_range` VARCHAR(255)         DEFAULT NULL COMMENT '服务范围',
    `entry_time`    DATE                 DEFAULT NULL COMMENT '入职时间',
    `score`         DECIMAL(3, 2)        DEFAULT 5.00 COMMENT '评分',
    `status`        TINYINT              DEFAULT 1 COMMENT '状态 1在职 0离职',
    `create_time`   DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='美容师表';

-- ------------------------------------------------------------
-- 美容师排班（防超卖依赖 uk_beautician_date_slot + 条件更新）
-- ------------------------------------------------------------
CREATE TABLE `beautician_schedule`
(
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `beautician_id` BIGINT      NOT NULL COMMENT '美容师ID',
    `date`          DATE        NOT NULL COMMENT '日期',
    `time_slot`     VARCHAR(50) NOT NULL COMMENT '时段，如14:00-16:00',
    `available`     TINYINT              DEFAULT 1 COMMENT '是否可接单 1可接单 0已占用',
    `order_id`      BIGINT               DEFAULT NULL COMMENT '占用订单ID',
    `create_time`   DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_beautician_date_slot` (`beautician_id`, `date`, `time_slot`),
    KEY `idx_beautician_id` (`beautician_id`),
    KEY `idx_date` (`date`),
    KEY `idx_available` (`available`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='美容师排班表';

-- ------------------------------------------------------------
-- 预约车（当前实现走 Redis，此表保留用于后续持久化）
-- ------------------------------------------------------------
CREATE TABLE `reserve_cart`
(
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT NOT NULL COMMENT '用户ID',
    `service_id`  BIGINT NOT NULL COMMENT '服务项目ID',
    `quantity`    INT             DEFAULT 1 COMMENT '数量',
    `create_time` DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='预约车表';

-- ------------------------------------------------------------
-- 服务预约单
-- ------------------------------------------------------------
CREATE TABLE `service_order`
(
    `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_number`     VARCHAR(50)    NOT NULL COMMENT '订单号',
    `user_id`          BIGINT         NOT NULL COMMENT '用户ID',
    `address_id`       BIGINT                  DEFAULT NULL COMMENT '地址ID',
    `pet_id`           BIGINT                  DEFAULT NULL COMMENT '宠物ID',
    `beautician_id`    BIGINT                  DEFAULT NULL COMMENT '美容师ID',
    `schedule_id`      BIGINT                  DEFAULT NULL COMMENT '排班时段ID',
    `appointment_date` DATE                    DEFAULT NULL COMMENT '预约日期',
    `time_slot`        VARCHAR(50)             DEFAULT NULL COMMENT '预约时段',
    `total_amount`     DECIMAL(10, 2) NOT NULL COMMENT '总金额',
    `status`           TINYINT                 DEFAULT 1 COMMENT '1待支付 2待确认 3待上门 4服务中 5已完成 6已取消 7待评价',
    `message_id`       VARCHAR(64)             DEFAULT NULL COMMENT 'MQ延迟消息ID',
    `reminder_status`  TINYINT                 DEFAULT 0 COMMENT '提醒状态 0未提醒 1已提醒 2已取消',
    `pay_time`         DATETIME                DEFAULT NULL COMMENT '支付时间',
    `remark`           VARCHAR(255)            DEFAULT NULL COMMENT '备注',
    `create_time`      DATETIME                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_number` (`order_number`),
    -- 我的订单列表：user_id + status 过滤 + 时间排序
    KEY `idx_user_status_create` (`user_id`, `status`, `create_time`),
    -- 管理端按美容师 + 日期查订单
    KEY `idx_beautician_date` (`beautician_id`, `appointment_date`),
    -- 超时取消 / 状态扫描
    KEY `idx_status_create` (`status`, `create_time`),
    KEY `idx_message_id` (`message_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='服务预约单表';

-- ------------------------------------------------------------
-- 订单明细
-- ------------------------------------------------------------
CREATE TABLE `order_detail`
(
    `id`          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`    BIGINT         NOT NULL COMMENT '订单ID',
    `service_id`  BIGINT         NOT NULL COMMENT '服务项目ID',
    `name`        VARCHAR(100)   NOT NULL COMMENT '服务名称（快照）',
    `price`       DECIMAL(10, 2) NOT NULL COMMENT '服务价格（快照）',
    `quantity`    INT                     DEFAULT 1 COMMENT '数量',
    `amount`      DECIMAL(10, 2) NOT NULL COMMENT '小计金额',
    `create_time` DATETIME                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='订单明细表';

-- ------------------------------------------------------------
-- 订单评价（uk_order_id 防止重复评价）
-- ------------------------------------------------------------
CREATE TABLE `order_comment`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`      BIGINT       NOT NULL COMMENT '订单ID',
    `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
    `beautician_id` BIGINT                DEFAULT NULL COMMENT '美容师ID',
    `score`         TINYINT      NOT NULL COMMENT '评分 1-5',
    `content`       VARCHAR(500)          DEFAULT NULL COMMENT '评价内容',
    `create_time`   DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_id` (`order_id`),
    KEY `idx_beautician_id` (`beautician_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='订单评价表';

-- ------------------------------------------------------------
-- 支付流水（uk_trade_no 保证回调幂等）
-- ------------------------------------------------------------
CREATE TABLE `payment_record`
(
    `id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`      BIGINT         NOT NULL COMMENT '订单ID',
    `order_number`  VARCHAR(32)    NOT NULL COMMENT '商户订单号',
    `channel`       VARCHAR(32)    NOT NULL COMMENT '支付渠道',
    `trade_no`      VARCHAR(64)             DEFAULT NULL COMMENT '渠道交易号',
    `amount`        DECIMAL(10, 2) NOT NULL COMMENT '支付金额',
    `status`        TINYINT        NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已关闭',
    `callback_body` VARCHAR(2000)           DEFAULT NULL COMMENT '回调原始报文',
    `callback_time` DATETIME                DEFAULT NULL COMMENT '回调时间',
    `create_time`   DATETIME                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_trade_no` (`trade_no`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_status_create` (`status`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='支付流水表';

-- ------------------------------------------------------------
-- AI 问答历史
-- ------------------------------------------------------------
CREATE TABLE `ai_conversation`
(
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT NOT NULL COMMENT '用户ID',
    `pet_id`      BIGINT          DEFAULT NULL COMMENT '关联宠物ID',
    `question`    TEXT   NOT NULL COMMENT '问题',
    `answer`      TEXT   NOT NULL COMMENT '答案',
    `references`  TEXT COMMENT '参考来源JSON',
    `create_time` DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '提问时间',
    `update_time` DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_create` (`user_id`, `create_time`),
    KEY `idx_pet_id` (`pet_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI问答历史表';

-- ------------------------------------------------------------
-- 知识库文档
-- ------------------------------------------------------------
CREATE TABLE `knowledge_doc`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`        VARCHAR(200) NOT NULL COMMENT '标题',
    `category`     VARCHAR(50)           DEFAULT NULL COMMENT '分类：护理/饮食/疾病预防',
    `content`      TEXT COMMENT '内容',
    `enabled`      TINYINT               DEFAULT 1 COMMENT '是否启用 1启用 0禁用',
    `index_status` TINYINT               DEFAULT 0 COMMENT '索引状态 0未索引 1索引中 2正常 3失败',
    `create_time`  DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_title` (`title`),
    KEY `idx_enabled_index` (`enabled`, `index_status`),
    KEY `idx_category` (`category`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='知识库文档表';
