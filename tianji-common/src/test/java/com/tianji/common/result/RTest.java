package com.tianji.common.result;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RTest {

    @Test
    void okWithoutDataShouldReturn200() {
        R<Void> result = R.ok();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getData()).isNull();
    }

    @Test
    void okWithDataShouldReturn200AndData() {
        R<String> result = R.ok("hello");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getData()).isEqualTo("hello");
    }

    @Test
    void okWithNullDataShouldReturn200() {
        R<String> result = R.ok(null);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getData()).isNull();
    }

    @Test
    void failWithCodeAndMessage() {
        R<Void> result = R.fail(404, "资源未找到");

        assertThat(result.getCode()).isEqualTo(404);
        assertThat(result.getMessage()).isEqualTo("资源未找到");
        assertThat(result.getData()).isNull();
    }

    @Test
    void failWithMessageOnlyShouldDefaultCodeTo500() {
        R<Void> result = R.fail("服务器错误");

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("服务器错误");
        assertThat(result.getData()).isNull();
    }

    @Test
    void shouldSupportGenericDataTypes() {
        R<Integer> intResult = R.ok(42);
        assertThat(intResult.getData()).isEqualTo(42);

        R<Long> longResult = R.ok(999L);
        assertThat(longResult.getData()).isEqualTo(999L);
    }

    @Test
    void shouldSetCodeAndMessage() {
        R<String> result = R.ok("test");
        result.setCode(201);
        result.setMessage("created");

        assertThat(result.getCode()).isEqualTo(201);
        assertThat(result.getMessage()).isEqualTo("created");
    }
}
