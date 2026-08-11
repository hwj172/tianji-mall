package com.tianji.mall.feign.fallback;

import com.tianji.common.result.R;
import com.tianji.mall.feign.UserFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class UserFeignClientFallback implements FallbackFactory<UserFeignClient> {

    @Override
    public UserFeignClient create(Throwable cause) {
        log.error("UserFeignClient 调用失败，触发降级", cause);
        return new UserFeignClient() {
            @Override
            public R<Map<String, Object>> getUserById(Long id) {
                return R.fail(500, "用户服务暂不可用");
            }

            @Override
            public R<Long> countUsers() {
                return R.ok(0L);
            }

            @Override
            public R<Void> promoteToSeller(Long userId) {
                return R.fail(500, "用户服务暂不可用");
            }

            @Override
            public R<Map> listUsers(int page, int size, String keyword, String role, Integer status) {
                Map<String, Object> empty = new java.util.HashMap<>();
                empty.put("records", Collections.emptyList());
                empty.put("total", 0);
                return R.ok(empty);
            }

            @Override
            public R<Void> updateUserStatus(Long id, Integer status) {
                return R.fail(500, "用户服务暂不可用");
            }

            @Override
            public R<Void> updateUserRole(Long id, String role) {
                return R.fail(500, "用户服务暂不可用");
            }

            @Override
            public R<List<Map<String, Object>>> getPendingProfiles() {
                return R.ok(Collections.emptyList());
            }

            @Override
            public R<Void> auditProfile(Long id, boolean approve) {
                return R.fail(500, "用户服务暂不可用");
            }
        };
    }
}
