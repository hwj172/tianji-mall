package com.tianji.common.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityTest {

    @Test
    void shouldSetAndGetCreateTime() {
        TestEntity entity = new TestEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        assertThat(entity.getCreateTime()).isEqualTo(now);
    }

    @Test
    void shouldSetAndGetUpdateTime() {
        TestEntity entity = new TestEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setUpdateTime(now);
        assertThat(entity.getUpdateTime()).isEqualTo(now);
    }

    @Test
    void fieldsShouldBeNullByDefault() {
        TestEntity entity = new TestEntity();
        assertThat(entity.getCreateTime()).isNull();
        assertThat(entity.getUpdateTime()).isNull();
    }

    static class TestEntity extends BaseEntity {
    }
}
