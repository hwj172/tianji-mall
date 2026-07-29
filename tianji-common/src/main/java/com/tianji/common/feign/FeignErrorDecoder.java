package com.tianji.common.feign;

import com.tianji.common.exception.BizException;
import com.tianji.common.exception.ServiceUnavailableException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一 Feign 错误解码器。
 * 4xx → BizException（不可重试）
 * 5xx → ServiceUnavailableException（可重试）
 */
@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        log.warn("Feign 调用失败: method={}, status={}", methodKey, status);

        if (status >= 400 && status < 500) {
            return new BizException(400, "下游服务请求错误: " + status);
        }
        if (status >= 500) {
            return new ServiceUnavailableException("下游服务异常: " + status);
        }
        return defaultDecoder.decode(methodKey, response);
    }
}
