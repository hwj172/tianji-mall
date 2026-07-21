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
  `role`        VARCHAR(20)  NOT NULL DEFAULT 'user' COMMENT '用户角色：user-普通用户 admin-管理员',
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
  `sales`       INT            NOT NULL DEFAULT 0 COMMENT '销量',
  `category_id` BIGINT         NOT NULL COMMENT '所属分类ID',
  `images`      VARCHAR(2048)  DEFAULT NULL COMMENT '商品图片（JSON数组）',
  `status`      TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1-上架 0-下架',
  `create_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`),
  FULLTEXT INDEX `ft_name_desc` (`name`, `description`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- 4. 购物车表
CREATE TABLE IF NOT EXISTS `cart_item` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '购物车项ID',
  `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
  `product_id`  BIGINT   NOT NULL COMMENT '商品ID',
  `quantity`    INT      NOT NULL DEFAULT 1 COMMENT '数量',
  `sku_id`      BIGINT   DEFAULT NULL COMMENT 'SKU ID（有规格时必须选）',
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
  `address_id`         BIGINT         NOT NULL COMMENT '收货地址ID',
  `logistics_company`  VARCHAR(64)    DEFAULT NULL COMMENT '物流公司',
  `tracking_number`    VARCHAR(64)    DEFAULT NULL COMMENT '快递单号',
  `receive_time`       DATETIME       DEFAULT NULL COMMENT '收货时间',
  `create_time`        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
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
  `sku_id`       BIGINT        DEFAULT NULL COMMENT 'SKU ID',
  `sku_specs`    VARCHAR(512)  DEFAULT NULL COMMENT 'SKU规格快照',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';

-- 8. 退款表
CREATE TABLE IF NOT EXISTS `refund` (
  `id`              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '退款ID',
  `order_id`        BIGINT         NOT NULL COMMENT '订单ID',
  `user_id`         BIGINT         NOT NULL COMMENT '用户ID',
  `amount`          DECIMAL(10,2)  NOT NULL COMMENT '退款金额',
  `reason`          VARCHAR(500)   NOT NULL COMMENT '退款原因',
  `status`          VARCHAR(20)    NOT NULL DEFAULT 'processing' COMMENT '退款状态：processing/success/fail',
  `alipay_refund_no` VARCHAR(64)   DEFAULT NULL COMMENT '支付宝退款单号',
  `fail_reason`     VARCHAR(500)   DEFAULT NULL COMMENT '失败原因',
  `created_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款表';

-- 10. 用户评价表
CREATE TABLE IF NOT EXISTS `review` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
  `product_id`  BIGINT       NOT NULL COMMENT '商品ID',
  `order_id`    BIGINT       NOT NULL COMMENT '订单ID',
  `rating`      TINYINT      NOT NULL COMMENT '评分：1-5',
  `content`     VARCHAR(1000) DEFAULT NULL COMMENT '评价内容',
  `images`      VARCHAR(2048) DEFAULT NULL COMMENT '评价图片（JSON数组）',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-审核通过 0-待审核',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_user_id` (`user_id`),
  UNIQUE KEY `uk_user_order_product` (`user_id`, `order_id`, `product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户评价表';

-- 12. 优惠券表
CREATE TABLE IF NOT EXISTS `coupon` (
  `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '优惠券ID',
  `name`             VARCHAR(128)   NOT NULL COMMENT '优惠券名称',
  `discount_type`    VARCHAR(20)    NOT NULL COMMENT '类型：FIXED-满减 PERCENT-折扣',
  `discount_value`   DECIMAL(10,2)  NOT NULL COMMENT '优惠值（满减为金额，折扣为每10元折扣）',
  `min_order_amount` DECIMAL(10,2)  NOT NULL DEFAULT 0.00 COMMENT '最低消费金额',
  `total_quantity`   INT            NOT NULL COMMENT '发放总量',
  `used_quantity`    INT            NOT NULL DEFAULT 0 COMMENT '已领取数量',
  `status`           TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-停用',
  `start_time`       DATETIME       NOT NULL COMMENT '开始时间',
  `end_time`         DATETIME       NOT NULL COMMENT '结束时间',
  `create_time`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券表';

-- 13. 用户优惠券表
CREATE TABLE IF NOT EXISTS `user_coupon` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
  `coupon_id`     BIGINT       NOT NULL COMMENT '优惠券ID',
  `status`        VARCHAR(20)  NOT NULL DEFAULT 'UNUSED' COMMENT '状态：UNUSED/USED/EXPIRED',
  `used_time`     DATETIME     DEFAULT NULL COMMENT '使用时间',
  `used_order_id` BIGINT       DEFAULT NULL COMMENT '使用的订单ID',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_coupon_id` (`coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户优惠券表';

-- 14. 用户收藏表
CREATE TABLE IF NOT EXISTS `favorite` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
  `product_id`  BIGINT   NOT NULL COMMENT '商品ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏表';

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

-- ============================================================
-- 商品 SKU（规格组合）
-- ============================================================
CREATE TABLE IF NOT EXISTS `product_sku` (
  `id`          BIGINT         NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
  `product_id`  BIGINT         NOT NULL COMMENT '商品ID',
  `sku_code`    VARCHAR(128)   DEFAULT NULL COMMENT '商家自定义SKU编码',
  `specs`       VARCHAR(512)   NOT NULL COMMENT '规格组合（如"颜色:深空黑;容量:256G"）',
  `price`       DECIMAL(10,2)  DEFAULT NULL COMMENT 'SKU价格（NULL=使用商品默认价）',
  `stock`       INT            NOT NULL DEFAULT 0 COMMENT 'SKU库存',
  `sales`       INT            NOT NULL DEFAULT 0 COMMENT 'SKU销量',
  `status`      TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
  `create_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品SKU表';

-- ============================================================
-- 商品属性参数
-- ============================================================
CREATE TABLE IF NOT EXISTS `product_attribute` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '属性ID',
  `product_id`  BIGINT       NOT NULL COMMENT '商品ID',
  `name`        VARCHAR(64)  NOT NULL COMMENT '属性名（如"屏幕尺寸"）',
  `value`       VARCHAR(256) NOT NULL COMMENT '属性值（如"6.1英寸"）',
  `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品属性表';
