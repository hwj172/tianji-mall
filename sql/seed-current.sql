-- =============================================
-- 天机商城 测试数据（基于当前项目 33 张表结构生成）
-- 2026-08-08 重置重建：覆盖会员/秒杀/拼团/店铺/退款/优惠券/SKU/满减 全功能
-- 密码统一: 123456 (BCrypt)
-- 执行: mysql -uroot -proot tianji_mall < seed-current.sql
-- =============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============ 清空业务表 ============
TRUNCATE TABLE address;
TRUNCATE TABLE ai_conversation;
TRUNCATE TABLE banner;
TRUNCATE TABLE browsing_history;
TRUNCATE TABLE cart_item;
TRUNCATE TABLE category;
TRUNCATE TABLE coupon;
TRUNCATE TABLE favorite;
TRUNCATE TABLE group_buy;
TRUNCATE TABLE group_buy_order;
TRUNCATE TABLE group_buy_participant;
TRUNCATE TABLE logistics_track;
TRUNCATE TABLE notification;
TRUNCATE TABLE operation_log;
TRUNCATE TABLE `order`;
TRUNCATE TABLE order_item;
TRUNCATE TABLE payment;
TRUNCATE TABLE points_log;
TRUNCATE TABLE product;
TRUNCATE TABLE product_attribute;
TRUNCATE TABLE product_similarity;
TRUNCATE TABLE product_sku;
TRUNCATE TABLE promotion;
TRUNCATE TABLE refund;
TRUNCATE TABLE refund_item;
TRUNCATE TABLE review;
TRUNCATE TABLE search_log;
TRUNCATE TABLE shop;
TRUNCATE TABLE shop_follow;
TRUNCATE TABLE sign_in;
TRUNCATE TABLE `user`;
TRUNCATE TABLE user_coupon;
TRUNCATE TABLE user_member;

-- ============ 用户（6） ============
-- 密码 123456
INSERT INTO `user` (`id`, `username`, `password`, `phone`, `email`, `avatar`, `status`, `role`, `profile_status`, `pending_username`, `pending_avatar`) VALUES
(1, 'admin',       '$2b$12$B2bsAOk6RuH5cXZiCoXkV.B9bHPDqQ2DD2DReVmikoFBEgfPMR/jq', '13900139000', 'admin@tianji.com',   NULL, 1, 'admin',  'approved', NULL, NULL),
(2, 'seller_demo', '$2b$12$B2bsAOk6RuH5cXZiCoXkV.B9bHPDqQ2DD2DReVmikoFBEgfPMR/jq', '13800138000', 'seller@tianji.com',  NULL, 1, 'seller', 'approved', NULL, NULL),
(3, 'testuser',    '$2b$12$B2bsAOk6RuH5cXZiCoXkV.B9bHPDqQ2DD2DReVmikoFBEgfPMR/jq', '13700137000', 'test@tianji.com',    NULL, 1, 'user',   'approved', NULL, NULL),
(4, 'buyer_wang',  '$2b$12$B2bsAOk6RuH5cXZiCoXkV.B9bHPDqQ2DD2DReVmikoFBEgfPMR/jq', '13600136000', 'wang@tianji.com',    NULL, 1, 'user',   'approved', NULL, NULL),
(5, 'seller_li',   '$2b$12$B2bsAOk6RuH5cXZiCoXkV.B9bHPDqQ2DD2DReVmikoFBEgfPMR/jq', '13500135000', 'lishop@tianji.com',  NULL, 1, 'seller', 'approved', NULL, NULL),
(6, 'buyer_zhao',  '$2b$12$B2bsAOk6RuH5cXZiCoXkV.B9bHPDqQ2DD2DReVmikoFBEgfPMR/jq', '13400134000', 'zhao@tianji.com',    NULL, 1, 'user',   'pending',  'zhao_renamed', '/uploads/demo_avatar.png');

-- ============ 会员（6） ============
INSERT INTO `user_member` (`user_id`, `level`, `points`, `total_points`) VALUES
(1, 5, 12000, 12000),
(2, 4, 8600, 9200),
(3, 3, 2350, 3500),
(4, 2, 800, 900),
(5, 3, 1500, 1800),
(6, 1, 120, 120);

-- ============ 签到 / 积分日志 ============
INSERT INTO `sign_in` (`user_id`, `sign_date`) VALUES
(3, CURDATE()),
(3, DATE_SUB(CURDATE(), INTERVAL 1 DAY)),
(3, DATE_SUB(CURDATE(), INTERVAL 2 DAY)),
(4, CURDATE());

INSERT INTO `points_log` (`user_id`, `change_type`, `points`, `remark`) VALUES
(3, 'SIGN_IN',    10,  '每日签到'),
(3, 'ORDER',      100, '订单消费积分'),
(3, 'SIGN_IN',    20,  '连续签到奖励');

-- ============ 店铺（2） ============
INSERT INTO `shop` (`id`, `name`, `logo`, `description`, `seller_id`, `status`, `notice`) VALUES
(1, '天机数码旗舰店', 'https://images.unsplash.com/photo-1553406830-ef2513450d76?w=200&q=80', '数码 3C 专营，正品保障，闪电发货', 2, 1, '本店商品均支持 7 天无理由退换'),
(2, 'Li 运动户外店',  'https://images.unsplash.com/photo-1465453869711-7e174808ace9?w=200&q=80', '运动鞋服，潮流装备', 5, 1, '满 200 包邮');

-- ============ 分类（3 顶级 + 6 子级） ============
INSERT INTO `category` (`id`, `name`, `parent_id`, `sort`) VALUES
(1, '手机数码', 0, 1),
(2, '电脑办公', 0, 2),
(3, '服饰鞋包', 0, 3),
(11, '手机',     1, 1),
(12, '智能穿戴', 1, 2),
(21, '笔记本电脑', 2, 1),
(22, '电脑配件', 2, 2),
(31, '运动鞋',   3, 1),
(32, '服饰箱包', 3, 2);

-- ============ Banner ============
INSERT INTO `banner` (`id`, `title`, `image_url`, `link_url`, `sort`, `status`) VALUES
(1, '新品首发 · iPhone 16',  'https://images.unsplash.com/photo-1591337676887-a217a6970a8a?w=1200&q=80', '/product/1',  1, 1),
(2, '限时秒杀 · ThinkPad',  'https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=1200&q=80', '/seckill',     2, 1),
(3, '阶梯拼团 · 罗技鼠标',  'https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=1200&q=80', '/groupbuy',    3, 1);

-- ============ 商品（12） ============
-- 平台商品 1-6（含 SKU 商品 id=1、秒杀商品 id=5）
INSERT INTO `product` (`id`, `name`, `description`, `price`, `stock`, `category_id`, `images`, `status`, `sales`, `shop_id`, `seckill_price`, `seckill_stock`, `seckill_start_time`, `seckill_end_time`) VALUES
(1,  'iPhone 16 Pro 手机 256GB 深空黑',   'A18 Pro 芯片，4800 万三摄，钛金属边框', 9999.00, 50, 11, '["https://images.unsplash.com/photo-1591337676887-a217a6970a8a?w=400&q=80","https://images.unsplash.com/photo-1601784551446-20c9e07cdbdb?w=400&q=80"]', 1, 32, NULL, NULL, NULL, NULL, NULL),
(2,  '华为 Mate 70 Pro 旗舰手机 512GB',   '麒麟 9100，卫星通信，XMAGE 影像', 6999.00, 30, 11, '["https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=400&q=80"]', 1, 25, NULL, NULL, NULL, NULL, NULL),
(3,  '小米 15 Ultra 手机 512GB 摄影套装',  '徕卡光学，2 亿像素长焦，120W 快充', 5999.00, 80, 11, '["https://images.unsplash.com/photo-1601784551446-20c9e07cdbdb?w=400&q=80"]', 1, 40, NULL, NULL, NULL, NULL, NULL),
(4,  'MacBook Pro 14 笔记本电脑 M4 Pro',   'M4 Pro，24GB 内存，1TB SSD', 14999.00, 25, 21, '["https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=400&q=80"]', 1, 18, NULL, NULL, NULL, NULL, NULL),
(5,  'ThinkPad X1 Carbon Gen 12 商务本',  'Intel Ultra 7，2.8K OLED，1.09kg 超轻', 14999.00, 15, 21, '["https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?w=400&q=80"]', 1, 12, NULL, 12999.00, 8, '2026-01-01 00:00:00', '2027-01-01 00:00:00'),
(6,  '罗技 MX Master 3S 无线鼠标',         '8K DPI，MagSpeed，多设备切换', 599.00, 200, 22, '["https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=400&q=80"]', 1, 66, NULL, NULL, NULL, NULL, NULL);

-- 店铺商品 7-12
INSERT INTO `product` (`id`, `name`, `description`, `price`, `stock`, `category_id`, `images`, `status`, `sales`, `shop_id`) VALUES
(7,  'Nike Air Jordan 1 复刻运动鞋',      '经典复刻，Air Sole 气垫', 1299.00, 100, 31, '["https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=400&q=80"]', 1, 45, 1),
(8,  'Adidas Samba OG 经典板鞋',          '复古皮革板鞋，百搭', 899.00, 150, 31, '["https://images.unsplash.com/photo-1549298916-b41d501d3772?w=400&q=80"]', 1, 38, 1),
(9,  '优衣库轻薄羽绒服 冬季外套',         '90% 鹅绒，轻便保暖', 499.00, 300, 32, '["https://images.unsplash.com/photo-1539533018447-63fcce2678e3?w=400&q=80"]', 1, 88, 1),
(10, '新秀丽双肩背包 17.3 英寸',          '防泼水，电脑隔层，USB 充电口', 299.00, 120, 32, '["https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=400&q=80"]', 1, 56, 1),
(11, '华为 MateBook X Pro 轻薄本 2024',   'Ultra 9，3.1K OLED 触屏', 8999.00, 20, 21, '["https://images.unsplash.com/photo-1531297484001-80022131f5a1?w=400&q=80"]', 1, 9, 1),
(12, '小米手环 9 智能手环',               'AMOLED 屏，150+ 运动模式，21 天续航', 249.00, 500, 12, '["https://images.unsplash.com/photo-1524805444758-089113d48a6d?w=400&q=80"]', 1, 120, 2),
(13, 'iPad Pro 11 平板电脑 256G',         'M4 芯片，OLED 显示屏，Apple Pencil Pro 支持', 8999.00, 40, 11, '["https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?w=400&q=80"]', 2, 0, 2);

-- ============ SKU（iPhone 3 规格） ============
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `specs`, `price`, `stock`, `sales`, `status`) VALUES
(1, 1, 'IP16-256-BLACK', '颜色:深空黑;容量:256G', 9999.00, 20, 15, 1),
(2, 1, 'IP16-512-BLACK', '颜色:深空黑;容量:512G', 11999.00, 15, 10, 1),
(3, 1, 'IP16-512-TITAN', '颜色:原色钛;容量:512G', 11999.00, 15, 7, 1);

-- ============ 商品属性 ============
INSERT INTO `product_attribute` (`id`, `product_id`, `name`, `value`, `sort`) VALUES
(1, 1, '屏幕尺寸', '6.3 英寸', 1),
(2, 1, '芯片', 'A18 Pro', 2),
(3, 1, '续航', '视频 27 小时', 3),
(4, 4, '内存', '24GB 统一内存', 1),
(5, 4, '硬盘', '1TB SSD', 2);

-- ============ 商品相似度 ============
INSERT INTO `product_similarity` (`product_id`, `similar_product_id`, `co_count`, `score`) VALUES
(1, 2, 8, 0.8600),
(2, 1, 8, 0.8600),
(2, 3, 5, 0.7200),
(3, 2, 5, 0.7200),
(7, 8, 6, 0.7800),
(8, 7, 6, 0.7800);

-- ============ 收货地址 ============
INSERT INTO `address` (`id`, `user_id`, `receiver_name`, `phone`, `province`, `city`, `district`, `detail`, `is_default`) VALUES
(1, 3, '测试用户', '13700137000', '广东省', '深圳市', '南山区', '科技园路 1 号 1001 室', 1),
(2, 3, '测试用户', '13700137000', '上海市', '上海市', '浦东新区', '张江高科园区 88 号', 0),
(3, 2, '商家演示', '13800138000', '浙江省', '杭州市', '西湖区', '文三路 100 号', 1),
(4, 4, '王买家',   '13600136000', '北京市', '北京市', '朝阳区', '望京 SOHO T1', 1),
(5, 6, '赵买家',   '13400134000', '四川省', '成都市', '武侯区', '天府大道 2000 号', 1);

-- ============ 购物车 ============
INSERT INTO `cart_item` (`id`, `user_id`, `product_id`, `quantity`, `sku_id`, `checked`) VALUES
(1, 3, 1, 1, 1, 1),
(2, 3, 8, 1, NULL, 1),
(3, 4, 2, 1, NULL, 1),
(4, 6, 12, 2, NULL, 1);

-- ============ 收藏 ============
INSERT INTO `favorite` (`id`, `user_id`, `product_id`) VALUES
(1, 3, 1),
(2, 3, 7),
(3, 4, 2),
(4, 6, 3);

-- ============ 浏览足迹 ============
INSERT INTO `browsing_history` (`user_id`, `product_id`) VALUES
(3, 1), (3, 7), (3, 9), (4, 2), (6, 5);

-- ============ 优惠券（5） ============
INSERT INTO `coupon` (`id`, `name`, `discount_type`, `discount_value`, `min_order_amount`, `total_quantity`, `used_quantity`, `status`, `start_time`, `end_time`, `applicable_category_id`, `applicable_product_id`, `is_newbie`) VALUES
(1, '新人专享 20 元券',  'FIXED',   20.00,  99.00,  1000, 10,  1, '2026-01-01 00:00:00', '2027-01-01 00:00:00', NULL, NULL, 1),
(2, '满 1000 减 100',    'FIXED',   100.00, 1000.00, 500, 5,   1, '2026-01-01 00:00:00', '2027-01-01 00:00:00', NULL, NULL, 0),
(3, '数码 95 折券',      'PERCENT', 5.00,   500.00, 300, 3,   1, '2026-01-01 00:00:00', '2027-01-01 00:00:00', 1,   NULL, 0),
(4, '电脑满 8000 减 600', 'FIXED',  600.00, 8000.00, 100, 1,  1, '2026-01-01 00:00:00', '2027-01-01 00:00:00', 21,  NULL, 0),
(5, '已过期券（演示）',   'FIXED',  50.00,  200.00, 100, 8,   1, '2026-01-01 00:00:00', '2026-06-01 00:00:00', NULL, NULL, 0);

-- ============ 我的优惠券 ============
INSERT INTO `user_coupon` (`id`, `user_id`, `coupon_id`, `status`, `used_time`, `used_order_id`) VALUES
(1, 3, 1, 'UNUSED',  NULL, NULL),
(2, 3, 2, 'UNUSED',  NULL, NULL),
(3, 3, 3, 'USED',    DATE_SUB(NOW(), INTERVAL 3 DAY), 4),
(4, 3, 5, 'EXPIRED', NULL, NULL),
(5, 4, 1, 'UNUSED',  NULL, NULL);

-- ============ 满减活动 ============
INSERT INTO `promotion` (`id`, `name`, `threshold`, `discount`, `start_time`, `end_time`, `status`) VALUES
(1, '全场满 2000 减 150', 2000.00, 150.00, '2026-01-01 00:00:00', '2027-01-01 00:00:00', 1);

-- ============ 订单（5 状态全覆盖） ============
INSERT INTO `order` (`id`, `order_no`, `user_id`, `total_amount`, `status`, `logistics_company`, `tracking_number`, `receive_time`, `pay_type`, `address_id`, `create_time`) VALUES
(1, '202608081000001', 3, 9999.00,  1, NULL, NULL, NULL, NULL, 1, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(2, '202608081000002', 3, 499.00,   2, NULL, NULL, NULL, 1,    1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(3, '202608081000003', 3, 299.00,   3, '顺丰速运', 'SF1234567890', NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(4, '202608081000004', 3, 599.00,   4, '圆通速递', 'YT9876543210', DATE_SUB(NOW(), INTERVAL 1 DAY), 1, 1, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(5, '202608081000005', 3, 8999.00,  5, NULL, NULL, NULL, NULL, 2, DATE_SUB(NOW(), INTERVAL 5 DAY)),
(6, '202608081000006', 4, 6999.00,  2, NULL, NULL, NULL, 1,    4, DATE_SUB(NOW(), INTERVAL 2 DAY));

-- ============ 订单明细 ============
INSERT INTO `order_item` (`id`, `order_id`, `product_id`, `product_name`, `price`, `quantity`, `sku_id`, `sku_specs`) VALUES
(1, 1, 1,  'iPhone 16 Pro 手机 256GB 深空黑',  9999.00, 1, 1, '颜色:深空黑;容量:256G'),
(2, 2, 9,  '优衣库轻薄羽绒服 冬季外套',         499.00,  1, NULL, NULL),
(3, 3, 10, '新秀丽双肩背包 17.3 英寸',          299.00,  1, NULL, NULL),
(4, 4, 6,  '罗技 MX Master 3S 无线鼠标',        599.00,  1, NULL, NULL),
(5, 5, 11, '华为 MateBook X Pro 轻薄本 2024',   8999.00, 1, NULL, NULL),
(6, 6, 2,  '华为 Mate 70 Pro 旗舰手机 512GB',   6999.00, 1, NULL, NULL);

-- ============ 支付记录 ============
INSERT INTO `payment` (`id`, `payment_no`, `order_id`, `user_id`, `amount`, `status`, `trade_no`, `create_time`) VALUES
(1, 'PAY20260808000001', 2, 3, 499.00,  2, '2026080822001000001', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(2, 'PAY20260808000002', 3, 3, 299.00,  2, '2026080822001000002', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(3, 'PAY20260808000003', 4, 3, 599.00,  2, '2026080822001000003', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(4, 'PAY20260808000004', 6, 4, 6999.00, 2, '2026080822001000004', DATE_SUB(NOW(), INTERVAL 2 DAY));

-- ============ 物流轨迹（订单 3 已发货） ============
INSERT INTO `logistics_track` (`id`, `order_id`, `status`, `description`, `location`, `track_time`) VALUES
(1, 3, 'PICKED_UP',       '包裹已揽收',        '深圳', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(2, 3, 'IN_TRANSIT',      '运输中，已到达转运中心', '深圳', DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 6 HOUR),
(3, 3, 'IN_TRANSIT',      '运输中，已发往杭州',    '广州', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(4, 3, 'OUT_FOR_DELIVERY','派送中，快递员正在配送', '杭州', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(5, 3, 'OUT_FOR_DELIVERY','派送中，预计今日送达',   '杭州', DATE_SUB(NOW(), INTERVAL 6 HOUR)),
(6, 4, 'DELIVERED',       '已签收',              '杭州', DATE_SUB(NOW(), INTERVAL 1 DAY));

-- ============ 退款（3：待寄回 / 已寄回 / 已完成） ============
INSERT INTO `refund` (`id`, `order_id`, `user_id`, `amount`, `reason`, `status`, `refund_type`, `return_status`, `tracking_number`, `tracking_company`) VALUES
(1, 2, 3, 499.00, '尺码不合适，申请退货',      'processing', 'RETURN_REFUND', NULL,     NULL, NULL),
(2, 3, 3, 299.00, '背包有划痕，申请退货退款',  'processing', 'RETURN_REFUND', 'SHIPPED', 'SF4455667788', '顺丰速运'),
(3, 4, 3, 599.00, '重复购买，申请退款',        'success',    'REFUND_ONLY',   NULL,     NULL, NULL);

INSERT INTO `refund_item` (`id`, `refund_id`, `order_item_id`, `product_id`, `sku_id`, `quantity`, `amount`) VALUES
(1, 1, 2, 9,  NULL, 1, 499.00),
(2, 2, 3, 10, NULL, 1, 299.00),
(3, 3, 4, 6,  NULL, 1, 599.00);

-- ============ 评价（含商家回复） ============
INSERT INTO `review` (`id`, `user_id`, `product_id`, `order_id`, `rating`, `content`, `images`, `status`, `reply`, `reply_time`) VALUES
(1, 3, 6, 4, 5, '鼠标手感很好，静音按键非常棒！', '["https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=200&q=80"]', 1, '感谢您的支持，祝您购物愉快！', NOW()),
(2, 4, 2, 6, 4, '手机拍照很强，就是发货有点慢', NULL, 1, NULL, NULL);

-- ============ 通知 ============
INSERT INTO `notification` (`id`, `user_id`, `type`, `title`, `content`, `related_order_id`, `is_read`) VALUES
(1, 3, 'ORDER_SHIPPED',      '订单已发货',        '您的订单 202608081000003 已发货：顺丰速运 SF1234567890', 3, 0),
(2, 3, 'ORDER_COMPLETED',    '订单已完成',        '您的订单 202608081000004 已确认收货', 4, 1),
(3, 0, 'SYSTEM_ANNOUNCEMENT','系统公告',          '商城夏季大促开启，满 2000 减 150，新人领 20 元券！', NULL, 0),
(4, 4, 'ORDER_PAID',         '支付成功',          '您的订单 202608081000006 已支付成功', 6, 0);

-- ============ 拼团（1 活动 + 1 进行中团） ============
INSERT INTO `group_buy` (`id`, `product_id`, `tiers`, `start_time`, `end_time`, `expire_hours`, `status`) VALUES
(1, 6, '[{"count":2,"discount":0.9},{"count":5,"discount":0.8}]', '2026-01-01 00:00:00', '2027-01-01 00:00:00', 24, 1);

INSERT INTO `group_buy_order` (`id`, `group_id`, `user_id`, `product_id`, `target_tier`, `current_count`, `status`, `expire_time`) VALUES
(1, 'GB20260808000001', 3, 6, 2, 1, 'OPEN', '2027-01-01 00:00:00');

INSERT INTO `group_buy_participant` (`id`, `group_buy_order_id`, `user_id`, `order_id`) VALUES
(1, 1, 3, 1);

-- ============ 店铺关注 ============
INSERT INTO `shop_follow` (`id`, `user_id`, `shop_id`) VALUES
(1, 3, 1),
(2, 4, 1),
(3, 6, 2);

-- ============ 搜索热词 ============
INSERT INTO `search_log` (`id`, `keyword`, `user_id`, `create_time`) VALUES
(1, '手机',     3, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(2, 'iPhone',   3, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(3, '笔记本电脑', 3, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(4, '运动鞋',   4, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(5, '耳机',     6, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(6, '鼠标',     3, DATE_SUB(NOW(), INTERVAL 3 DAY));

SET FOREIGN_KEY_CHECKS = 1;
