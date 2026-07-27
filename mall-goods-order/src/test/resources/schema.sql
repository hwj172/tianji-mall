CREATE TABLE IF NOT EXISTS category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64),
    parent_id BIGINT,
    sort INT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128),
    description LONGTEXT,
    price DECIMAL(10,2),
    stock INT,
    sales INT DEFAULT 0,
    category_id BIGINT,
    shop_id BIGINT DEFAULT NULL,
    images VARCHAR(1024),
    status INT DEFAULT 1,
    seckill_price DECIMAL(10,2),
    seckill_stock INT,
    seckill_start_time TIMESTAMP,
    seckill_end_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cart_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    product_id BIGINT,
    quantity INT,
    sku_id BIGINT,
    checked INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS address (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    receiver_name VARCHAR(64),
    phone VARCHAR(20),
    province VARCHAR(32),
    city VARCHAR(32),
    district VARCHAR(32),
    detail VARCHAR(256),
    is_default INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `order` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32),
    user_id BIGINT,
    total_amount DECIMAL(10,2),
    status INT,
    pay_type INT,
    address_id BIGINT,
    logistics_company VARCHAR(64),
    tracking_number  VARCHAR(64),
    receive_time     TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT,
    product_id BIGINT,
    product_name VARCHAR(128),
    price DECIMAL(10,2),
    quantity INT,
    sku_id BIGINT,
    sku_specs VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS refund (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT           NOT NULL,
    user_id         BIGINT           NOT NULL,
    amount          DECIMAL(10,2)    NOT NULL,
    reason          VARCHAR(500)     NOT NULL,
    status          VARCHAR(20)      NOT NULL DEFAULT 'processing',
    alipay_refund_no VARCHAR(64),
    fail_reason     VARCHAR(500),
    refund_type     VARCHAR(20)      NOT NULL DEFAULT 'REFUND_ONLY',
    return_status   VARCHAR(20)      DEFAULT NULL,
    tracking_number VARCHAR(50)      DEFAULT NULL,
    tracking_company VARCHAR(50)     DEFAULT NULL,
    created_at      TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP        DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS refund_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_id       BIGINT NOT NULL,
    order_item_id   BIGINT NOT NULL,
    product_id      BIGINT NOT NULL,
    sku_id          BIGINT DEFAULT NULL,
    quantity        INT NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS review (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    product_id  BIGINT NOT NULL,
    order_id    BIGINT NOT NULL,
    rating      TINYINT NOT NULL,
    content     VARCHAR(1000),
    images      VARCHAR(2048),
    status      TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_order_product UNIQUE (user_id, order_id, product_id)
);

CREATE TABLE IF NOT EXISTS logistics_track (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT NOT NULL,
    status      VARCHAR(32) NOT NULL,
    description VARCHAR(256) NOT NULL,
    location    VARCHAR(128),
    track_time  TIMESTAMP NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_lt_order (order_id)
);

CREATE TABLE IF NOT EXISTS browsing_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    product_id  BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_bh_user_time (user_id, create_time DESC),
    UNIQUE KEY uk_bh_user_product (user_id, product_id)
);

CREATE TABLE IF NOT EXISTS notification (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    type             VARCHAR(32) NOT NULL,
    title            VARCHAR(128) NOT NULL,
    content          VARCHAR(512) NOT NULL,
    related_order_id BIGINT,
    is_read          TINYINT DEFAULT 0,
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_n_user_read (user_id, is_read)
);

CREATE TABLE IF NOT EXISTS coupon (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                  VARCHAR(128) NOT NULL,
    discount_type         VARCHAR(20)  NOT NULL,
    discount_value        DECIMAL(10,2) NOT NULL,
    min_order_amount      DECIMAL(10,2) DEFAULT 0.00,
    total_quantity        INT NOT NULL,
    used_quantity         INT DEFAULT 0,
    status                TINYINT DEFAULT 1,
    start_time            TIMESTAMP NOT NULL,
    end_time              TIMESTAMP NOT NULL,
    applicable_category_id BIGINT DEFAULT NULL,
    applicable_product_id  BIGINT DEFAULT NULL,
    create_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_coupon (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    coupon_id     BIGINT NOT NULL,
    status        VARCHAR(20) DEFAULT 'UNUSED',
    used_time     TIMESTAMP,
    used_order_id BIGINT,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS favorite (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    product_id  BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_product (user_id, product_id)
);

CREATE TABLE IF NOT EXISTS product_sku (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sku_code VARCHAR(128),
    specs VARCHAR(512) NOT NULL,
    price DECIMAL(10,2),
    stock INT DEFAULT 0,
    sales INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS product_attribute (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    `value` VARCHAR(256) NOT NULL,
    sort INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS product_similarity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    similar_product_id BIGINT NOT NULL,
    co_count INT DEFAULT 0,
    score DECIMAL(10,4) DEFAULT 0,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pair (product_id, similar_product_id),
    KEY idx_product (product_id),
    KEY idx_score (score DESC)
);

CREATE TABLE IF NOT EXISTS shop_follow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    shop_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sf_user_shop (user_id, shop_id)
);

CREATE TABLE IF NOT EXISTS shop (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    logo VARCHAR(256),
    description VARCHAR(512),
    seller_id BIGINT NOT NULL,
    status TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_seller (seller_id)
);

CREATE TABLE IF NOT EXISTS group_buy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    tiers VARCHAR(1024) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    expire_hours INT DEFAULT 24,
    status TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product (product_id)
);

CREATE TABLE IF NOT EXISTS group_buy_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL DEFAULT 0,
    product_id BIGINT NOT NULL,
    target_tier INT NOT NULL,
    current_count INT DEFAULT 1,
    status VARCHAR(20) DEFAULT 'OPEN',
    expire_time TIMESTAMP NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_id (group_id),
    KEY idx_gbo_product (product_id),
    KEY idx_gbo_status (status)
);

CREATE TABLE IF NOT EXISTS group_buy_participant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_buy_order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order (order_id)
);

CREATE TABLE IF NOT EXISTS banner (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    image_url VARCHAR(512) NOT NULL,
    link_url VARCHAR(512) DEFAULT NULL,
    sort INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS search_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    keyword VARCHAR(128) NOT NULL,
    user_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_time (create_time)
);
