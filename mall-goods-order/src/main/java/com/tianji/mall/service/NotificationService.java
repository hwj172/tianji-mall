package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.mall.entity.Notification;
import com.tianji.mall.mapper.NotificationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class NotificationService extends ServiceImpl<NotificationMapper, Notification> {

    public void createNotification(Long userId, String type, String title, String content, Long relatedOrderId) {
        try {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(type);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setRelatedOrderId(relatedOrderId);
            notification.setIsRead(0);
            notification.setCreateTime(LocalDateTime.now());
            save(notification);
            log.info("通知已创建: userId={}, type={}, title={}", userId, type, title);
        } catch (Exception e) {
            log.error("创建通知失败: userId={}, type={}", userId, type, e);
        }
    }

    public Page<Notification> getList(Long userId, int page, int size) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    public long getUnreadCount(Long userId) {
        return baseMapper.countUnread(userId);
    }

    public void markRead(Long userId, Long id) {
        Notification notification = getById(id);
        if (notification != null && notification.getUserId().equals(userId) && notification.getIsRead() == 0) {
            notification.setIsRead(1);
            updateById(notification);
        }
    }

    public void markAllRead(Long userId) {
        baseMapper.markAllRead(userId);
    }
}
