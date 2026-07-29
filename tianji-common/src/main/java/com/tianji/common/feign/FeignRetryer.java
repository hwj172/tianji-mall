package com.tianji.common.feign;

import feign.RetryableException;
import feign.Retryer;

/**
 * Feign 不重试策略 — 失败直接抛异常，由上游 Sentinel 断路器处理。
 * 不自动重试的原因是 Feign 调用涉及非幂等操作（创建订单、退款等）。
 */
public class FeignRetryer implements Retryer {

    @Override
    public void continueOrPropagate(RetryableException e) {
        throw e;
    }

    @Override
    public Retryer clone() {
        return this;
    }
}
