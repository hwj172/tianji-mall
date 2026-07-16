-- =============================================
-- 天机商城 测试数据
-- =============================================

USE tianji_mall;

-- ==================== 测试用户 ====================
-- 密码: 123456 (BCrypt)
INSERT INTO `user` (`id`, `username`, `password`, `phone`, `email`, `status`) VALUES
(1, 'testuser', '$2b$12$B2bsAOk6RuH5cXZiCoXkV.B9bHPDqQ2DD2DReVmikoFBEgfPMR/jq', '13800138000', 'test@tianji.com', 1);

-- ==================== 商品分类 ====================
INSERT INTO `category` (`id`, `name`, `parent_id`, `sort`) VALUES
(1, '手机数码', 0, 1),
(2, '电脑办公', 0, 2),
(3, '服饰鞋包', 0, 3);

-- ==================== 商品 ====================

-- 手机数码（4 件）
INSERT INTO `product` (`id`, `name`, `description`, `price`, `stock`, `category_id`, `images`, `status`) VALUES
(1, 'iPhone 16 Pro Max 256GB', '全新 A18 Pro 芯片，钛金属设计，4800 万像素主摄，支持 Apple Intelligence', 9999.00, 50, 1, '["https://picsum.photos/seed/iphone16/400/400"]', 1),
(2, '华为 Mate 70 Pro 512GB', '麒麟 9100 芯片，卫星通信，XMAGE 影像，HarmonyOS NEXT', 6999.00, 30, 1, '["https://picsum.photos/seed/mate70/400/400"]', 1),
(3, '小米 15 Ultra 512GB', '骁龙 8 Gen4，徕卡光学镜头，120W 快充，小米澎湃 OS 2.0', 5999.00, 80, 1, '["https://picsum.photos/seed/mi15/400/400"]', 1),
(4, 'OPPO Find X8 Pro 256GB', '天玑 9400，哈苏联名影像，100W 超级闪充', 4999.00, 60, 1, '["https://picsum.photos/seed/findx8/400/400"]', 1);

-- 电脑办公（4 件）
INSERT INTO `product` (`id`, `name`, `description`, `price`, `stock`, `category_id`, `images`, `status`) VALUES
(5, 'MacBook Pro 14英寸 M4 Pro', 'Apple M4 Pro 芯片，24GB 统一内存，1TB SSD，Liquid Retina XDR 显示屏', 14999.00, 25, 2, '["https://picsum.photos/seed/macbook14/400/400"]', 1),
(6, 'ThinkPad X1 Carbon Gen 12', 'Intel Core Ultra 7，32GB RAM，1TB SSD，2.8K OLED，重 1.09kg', 10999.00, 15, 2, '["https://picsum.photos/seed/thinkpad/400/400"]', 1),
(7, '华为 MateBook X Pro 2024', 'Intel Core Ultra 9，32GB RAM，2TB SSD，3.1K OLED 触屏', 8999.00, 20, 2, '["https://picsum.photos/seed/matebook/400/400"]', 1),
(8, '罗技 MX Master 3S 鼠标', '人体工学设计，MagSpeed 滚轮，8K DPI，USB-C 充电，支持多设备切换', 599.00, 200, 2, '["https://picsum.photos/seed/mxmaster/400/400"]', 1);

-- 服饰鞋包（4 件）
INSERT INTO `product` (`id`, `name`, `description`, `price`, `stock`, `category_id`, `images`, `status`) VALUES
(9, 'Nike Air Jordan 1 Retro High OG', '经典复刻，Air Sole 气垫，全粒面皮革，橡胶外底', 1299.00, 100, 3, '["https://picsum.photos/seed/aj1/400/400"]', 1),
(10, 'Adidas Samba OG 经典运动鞋', '经典复古板鞋，皮革鞋面，橡胶大底，百搭款式', 899.00, 150, 3, '["https://picsum.photos/seed/samba/400/400"]', 1),
(11, '优衣库轻薄羽绒服', '90% 鹅绒填充，轻便保暖，防泼水处理，多色可选', 499.00, 300, 3, '["https://picsum.photos/seed/uniqlo/400/400"]', 1),
(12, '新秀丽双肩背包 17.3英寸', '防泼水面料，电脑隔层+弹力袋+手机袋+卡袋，USB 充电口，S 型肩带', 299.00, 120, 3, '["https://picsum.photos/seed/samsonite/400/400"]', 1);
