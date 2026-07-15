-- =============================================
-- 天机商城（tianji-mall）全量建表脚本
-- MySQL 8.0+ | InnoDB | utf8mb4
-- =============================================

CREATE DATABASE IF NOT EXISTS tianji_mall
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE tianji_mall;

-- ==================== 用户服务 ====================

-- 1. 用户表
CREATE TABLE IF NOT EXISTS `user` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username`    VARCHAR(64)  NOT NULL COMMENT '用户名',
  `password`    VARCHAR(128) NOT NULL COMMENT '密码（BCrypt）',
  `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  `email`       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
  `avatar`      VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-禁用',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ==================== 商城核心服务 ====================

-- 2. 商品分类表
CREATE TABLE IF NOT EXISTS `category` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `name`        VARCHAR(64) NOT NULL COMMENT '分类名称',
  `parent_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '父分类ID（0=顶级）',
  `sort`        INT         NOT NULL DEFAULT 0 COMMENT '排序（越小越前）',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';

-- 3. 商品表
CREATE TABLE IF NOT EXISTS `product` (
  `id`          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `name`        VARCHAR(256)   NOT NULL COMMENT '商品名称',
  `description` TEXT           DEFAULT NULL COMMENT '商品描述',
  `price`       DECIMAL(10,2)  NOT NULL COMMENT '价格（元）',
  `stock`       INT            NOT NULL DEFAULT 0 COMMENT '库存',
  `category_id` BIGINT         NOT NULL COMMENT '所属分类ID',
  `images`      VARCHAR(2048)  DEFAULT NULL COMMENT '商品图片（JSON数组）',
  `status`      TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1-上架 0-下架',
  `create_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- 4. 购物车表
CREATE TABLE IF NOT EXISTS `cart_item` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '购物车项ID',
  `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
  `product_id`  BIGINT   NOT NULL COMMENT '商品ID',
  `quantity`    INT      NOT NULL DEFAULT 1 COMMENT '数量',
  `checked`     TINYINT  NOT NULL DEFAULT 1 COMMENT '是否选中：1-是 0-否',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表';

-- 5. 收货地址表
CREATE TABLE IF NOT EXISTS `address` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '地址ID',
  `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
  `receiver_name` VARCHAR(32)  NOT NULL COMMENT '收件人姓名',
  `phone`         VARCHAR(20)  NOT NULL COMMENT '收件人电话',
  `province`      VARCHAR(32)  NOT NULL COMMENT '省',
  `city`          VARCHAR(32)  NOT NULL COMMENT '市',
  `district`      VARCHAR(32)  NOT NULL COMMENT '区',
  `detail`        VARCHAR(256) NOT NULL COMMENT '详细地址',
  `is_default`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认：1-是 0-否',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货地址表';

-- 6. 订单表
CREATE TABLE IF NOT EXISTS `order` (
  `id`           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no`     VARCHAR(32)    NOT NULL COMMENT '订单编号',
  `user_id`      BIGINT         NOT NULL COMMENT '用户ID',
  `total_amount` DECIMAL(10,2)  NOT NULL COMMENT '订单总金额',
  `status`       TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1-待付款 2-已付款 3-已发货 4-已完成 5-已取消',
  `pay_type`     TINYINT        DEFAULT NULL COMMENT '支付方式：1-支付宝',
  `address_id`   BIGINT         NOT NULL COMMENT '收货地址ID',
  `create_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- 7. 订单明细表
CREATE TABLE IF NOT EXISTS `order_item` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `order_id`     BIGINT        NOT NULL COMMENT '订单ID',
  `product_id`   BIGINT        NOT NULL COMMENT '商品ID',
  `product_name` VARCHAR(256)  NOT NULL COMMENT '商品名称（快照）',
  `price`        DECIMAL(10,2) NOT NULL COMMENT '单价（快照）',
  `quantity`     INT           NOT NULL COMMENT '数量',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';

-- ==================== 支付服务 ====================

-- 8. 支付记录表
CREATE TABLE IF NOT EXISTS `payment` (
  `id`          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '支付记录ID',
  `payment_no`  VARCHAR(64)    NOT NULL COMMENT '支付流水号',
  `order_id`    BIGINT         NOT NULL COMMENT '关联订单ID',
  `user_id`     BIGINT         NOT NULL COMMENT '用户ID',
  `amount`      DECIMAL(10,2)  NOT NULL COMMENT '支付金额',
  `status`      TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1-待支付 2-支付成功 3-支付失败 4-已退款',
  `trade_no`    VARCHAR(64)    DEFAULT NULL COMMENT '支付宝交易号',
  `create_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付记录表';

-- ==================== AI 智能导购服务 ====================

-- 9. AI 对话记录表
CREATE TABLE IF NOT EXISTS `ai_conversation` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '对话记录ID',
  `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
  `session_id`  VARCHAR(64)  NOT NULL COMMENT '会话ID',
  `role`        VARCHAR(16)  NOT NULL COMMENT '角色：user / assistant',
  `content`     TEXT         NOT NULL COMMENT '对话内容',
  `product_ids` VARCHAR(512) DEFAULT NULL COMMENT '关联商品ID（逗号分隔）',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_session` (`user_id`, `session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话记录表';
