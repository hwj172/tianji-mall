package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.entity.Notification;
import com.tianji.mall.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtUtil jwtUtil;

    @GetMapping("/list")
    public R<Page<Notification>> list(@RequestHeader("Authorization") String authHeader,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(notificationService.getList(userId, page, size));
    }

    @GetMapping("/unread-count")
    public R<Long> unreadCount(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(notificationService.getUnreadCount(userId));
    }

    @PutMapping("/{id}/read")
    public R<Void> markRead(@RequestHeader("Authorization") String authHeader,
                            @PathVariable("id") Long id) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        notificationService.markRead(userId, id);
        return R.ok();
    }

    @PutMapping("/read-all")
    public R<Void> markAllRead(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        notificationService.markAllRead(userId);
        return R.ok();
    }
}
