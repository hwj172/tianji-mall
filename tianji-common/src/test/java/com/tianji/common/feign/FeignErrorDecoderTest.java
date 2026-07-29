package com.tianji.common.feign;

import com.tianji.common.exception.BizException;
import com.tianji.common.exception.ServiceUnavailableException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class FeignErrorDecoderTest {

    private final FeignErrorDecoder decoder = new FeignErrorDecoder();

    private Response response(int status) {
        return Response.builder()
                .status(status)
                .request(Request.create(Request.HttpMethod.GET, "/test",
                        Collections.emptyMap(), null, StandardCharsets.UTF_8, null))
                .headers(new LinkedHashMap<>())
                .build();
    }

    @Test
    void shouldThrowBizExceptionFor4xx() {
        Exception e = decoder.decode("TestClient#method()", response(404));
        assertInstanceOf(BizException.class, e);
        assertTrue(e.getMessage().contains("404"));
    }

    @Test
    void shouldThrowServiceUnavailableExceptionFor5xx() {
        Exception e = decoder.decode("TestClient#method()", response(503));
        assertInstanceOf(ServiceUnavailableException.class, e);
        assertTrue(e.getMessage().contains("503"));
    }

    @Test
    void shouldThrowBizExceptionFor400() {
        Exception e = decoder.decode("TestClient#method()", response(400));
        assertInstanceOf(BizException.class, e);
    }

    @Test
    void shouldThrowServiceUnavailableExceptionFor500() {
        Exception e = decoder.decode("TestClient#method()", response(500));
        assertInstanceOf(ServiceUnavailableException.class, e);
    }
}
