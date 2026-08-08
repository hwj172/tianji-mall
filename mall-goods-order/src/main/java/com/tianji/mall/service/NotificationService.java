package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.mall.entity.Notification;
import com.tianji.mall.mapper.NotificationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    /**
     * 通知列表：返回本人通知 + 系统公告（userId=0）。
     * type：null/all 全部、order 订单通知（ORDER_ 前缀）、system 仅系统公告。
     */
    public Page<Notification> getList(Long userId, int page, int size, String type) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .and(w -> w.eq(Notification::getUserId, userId).or().eq(Notification::getUserId, 0L))
                .orderByDesc(Notification::getCreateTime);
        if (StringUtils.hasText(type) && !"all".equals(type)) {
            if ("order".equals(type)) {
                wrapper.like(Notification::getType, "ORDER_");
            } else if ("system".equals(type)) {
                wrapper.eq(Notification::getUserId, 0L);
            }
        }
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
