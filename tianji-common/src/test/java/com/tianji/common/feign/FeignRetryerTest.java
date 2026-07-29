package com.tianji.common.feign;

import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;

class FeignRetryerTest {

    @Test
    void shouldPropagateExceptionWithoutRetry() {
        FeignRetryer retryer = new FeignRetryer();
        RetryableException ex = new RetryableException(
                503, "Service Unavailable", Request.HttpMethod.GET, System.currentTimeMillis(),
                Request.create(Request.HttpMethod.GET, "/test",
                        Collections.emptyMap(), null, StandardCharsets.UTF_8, null));

        assertThrows(RetryableException.class, () -> retryer.continueOrPropagate(ex));
    }

    @Test
    void cloneShouldReturnSameType() {
        FeignRetryer retryer = new FeignRetryer();
        assertThrows(RetryableException.class, () -> {
            RetryableException ex = new RetryableException(
                    503, "Service Unavailable", Request.HttpMethod.GET, System.currentTimeMillis(),
                    Request.create(Request.HttpMethod.GET, "/test",
                            Collections.emptyMap(), null, StandardCharsets.UTF_8, null));
            retryer.clone().continueOrPropagate(ex);
        });
    }
}
