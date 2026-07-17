CREATE TABLE IF NOT EXISTS payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_no VARCHAR(64),
    order_id BIGINT,
    user_id BIGINT,
    amount DECIMAL(10,2),
    status INT,
    trade_no VARCHAR(128),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
