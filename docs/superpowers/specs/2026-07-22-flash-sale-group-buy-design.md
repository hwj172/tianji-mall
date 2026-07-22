# 秒杀 + 阶梯拼团 设计文档

## 概述

为天机商城增加秒杀和阶梯拼团两种促销能力。秒杀作为商品标签属性叠加（复用现商品表），阶梯拼团通过 group_buy + group_buy_order 两张新表实现。

## 数据模型

### 秒杀 — product 表扩展

```sql
ALTER TABLE product ADD COLUMN seckill_price DECIMAL(10,2);
ALTER TABLE product ADD COLUMN seckill_stock INT;
ALTER TABLE product ADD COLUMN seckill_start_time DATETIME;
ALTER TABLE product ADD COLUMN seckill_end_time DATETIME;
```

秒杀判定：`seckill_start_time ≤ now ≤ seckill_end_time` 且 `seckill_stock > 0`。秒杀与拼团互斥。

### 拼团 — 两张新表

```sql
CREATE TABLE IF NOT EXISTS group_buy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    tiers JSON NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    expire_hours INT DEFAULT 24,
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product (product_id)
);

CREATE TABLE IF NOT EXISTS group_buy_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id VARCHAR(32) NOT NULL,
    product_id BIGINT NOT NULL,
    target_tier INT NOT NULL,
    current_count INT DEFAULT 1,
    status VARCHAR(20) DEFAULT 'OPEN',
    expire_time DATETIME NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_id (group_id),
    KEY idx_product (product_id),
    KEY idx_status (status)
);
```

- `tiers` JSON 格式：`[{"count":2,"discount":0.9},{"count":5,"discount":0.8},{"count":10,"discount":0.7}]`
- 团状态：OPEN（进行中）/ FULL（满员）/ SUCCESS（成团）/ FAIL（失败）
- `expire_hours`：开团后有效期，超时未满团失败

## API

### 秒杀

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/product/{id}/seckill` | 后台设置秒杀 |
| DELETE | `/api/admin/product/{id}/seckill` | 后台取消秒杀 |
| GET | `/api/product/seckill/list` | 用户端秒杀商品列表（分页） |

秒杀下单复用 `POST /api/order/create`，OrderService 中判断秒杀窗口并取 seckill_price。

### 拼团

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/group-buy` | 后台创建拼团活动 |
| PUT | `/api/admin/group-buy/{id}` | 后台修改拼团活动 |
| GET | `/api/group-buy/list` | 用户端拼团活动列表 |
| GET | `/api/group-buy/{id}` | 拼团活动详情 + 进行中的团 |
| POST | `/api/group-buy/start` | 用户开团 |
| POST | `/api/group-buy/join/{groupId}` | 用户参团 |
| GET | `/api/group-buy/my` | 我参与的团 |

## 核心流程

### 秒杀下单

```
POST /api/order/create（复用现有）
  → OrderService.createOrder
    → 判断秒杀窗口 + seckill_stock > 0
    → 是：订单价 = seckill_price，扣 seckill_stock（原子 UPDATE WHERE seckill_stock >= qty）
    → 否：正常下单
    → 分布式锁、RocketMQ 等逻辑不变
```

### 拼团开团

```
POST /api/group-buy/start
  → 校验活动状态 + 秒杀互斥
  → 创建 group_buy_order（status=OPEN, current_count=1）
  → 创建订单（价格 = 原价 × 对应阶梯折扣）
  → 发送 RocketMQ 延迟消息（expire_hours 后检查）
```

### 参团

```
POST /api/group-buy/join/{groupId}
  → 校验团 OPEN + 未满 + 未过期
  → 原子 UPDATE current_count + 1 WHERE current_count < target_tier
  → 受影响行数=0 → 已满
  → 创建订单
  → 满员 → 标记 SUCCESS
```

### 超时检查

```
RocketMQ Consumer
  → 查团状态 OPEN 且 expire_time < now
  → 未满 → status=FAIL → 逐个退款（调用 PayFeignClient）
```

## 约束

- 一个商品同一时间只有一个拼团活动（product_id UNIQUE）
- 一个用户不能同时参加同一商品的两个团
- 秒杀商品不展示拼团入口（互斥）
- 秒杀库存用现有原子 UPDATE 模式，不引入 Redis 预扣

## 模块

- mall-goods-order：秒杀+拼团 API、Service、Mapper、定时/延迟任务、测试

## 测试

| 层级 | 内容 |
|------|------|
| Service 单元 | SeckillServiceTest：秒杀价/库存扣减/非窗口/互斥 ~5 个 |
| Service 单元 | GroupBuyServiceTest：开团/参团/满员/超时/防重复 ~7 个 |
| Controller | ProductControllerTest：新增 seckill/list 端点 ~2 个 |
| Controller | GroupBuyControllerTest：活动列表/详情/开团/参团 ~6 个 |
| Controller | AdminControllerTest：新增秒杀设置+拼团管理 ~5 个 |
