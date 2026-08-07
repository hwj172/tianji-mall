package com.tianji.mall.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要写入操作审计日志的写接口（Admin / Seller 管理操作）。
 * 由 AuditLogAspect 在方法成功执行后异步落库（best-effort，失败不影响主流程）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 操作动作，如 "delete_product" */
    String action();

    /** 对象类型，如 "product" */
    String targetType();

    /** 方法参数中对象 ID 的参数索引；默认 -1 表示从返回值/上下文取（此时 targetId 为 null） */
    int targetArg() default -1;
}
