package com.tianji.common.exception;

/**
 * 服务不可用异常 — 下游服务调用失败时抛出，区分于业务校验异常。
 * 上游可据此判断是否重试。
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
