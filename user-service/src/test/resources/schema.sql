CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(128) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(128),
    avatar VARCHAR(512),
    role VARCHAR(20) DEFAULT 'user',
    status INT DEFAULT 1,
    profile_status VARCHAR(20) DEFAULT 'approved',
    pending_username VARCHAR(64),
    pending_avatar VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
