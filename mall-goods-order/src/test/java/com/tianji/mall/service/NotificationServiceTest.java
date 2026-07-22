package com.tianji.mall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.mall.entity.Notification;
import com.tianji.mall.mapper.NotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationMapper notificationMapper;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService();
        ReflectionTestUtils.setField(notificationService, "baseMapper", notificationMapper);
    }

    @Test
    void shouldCreateNotification() {
        notificationService.createNotification(1L, "ORDER_SHIPPED", "订单已发货", "test", 100L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());

        Notification n = captor.getValue();
        assertThat(n.getUserId()).isEqualTo(1L);
        assertThat(n.getType()).isEqualTo("ORDER_SHIPPED");
        assertThat(n.getTitle()).isEqualTo("订单已发货");
        assertThat(n.getRelatedOrderId()).isEqualTo(100L);
        assertThat(n.getIsRead()).isEqualTo(0);
    }

    @Test
    void shouldGetUnreadCount() {
        when(notificationMapper.countUnread(1L)).thenReturn(5L);

        long count = notificationService.getUnreadCount(1L);

        assertThat(count).isEqualTo(5L);
        verify(notificationMapper).countUnread(1L);
    }

    @Test
    void shouldMarkSingleRead() {
        Notification n = new Notification();
        n.setId(10L);
        n.setUserId(1L);
        n.setIsRead(0);
        when(notificationMapper.selectById(10L)).thenReturn(n);
        when(notificationMapper.updateById(any(Notification.class))).thenReturn(1);

        notificationService.markRead(1L, 10L);

        assertThat(n.getIsRead()).isEqualTo(1);
        verify(notificationMapper).updateById(n);
    }

    @Test
    void shouldNotMarkReadForWrongUser() {
        Notification n = new Notification();
        n.setId(10L);
        n.setUserId(2L);
        n.setIsRead(0);
        when(notificationMapper.selectById(10L)).thenReturn(n);

        notificationService.markRead(1L, 10L);

        assertThat(n.getIsRead()).isEqualTo(0);
    }

    @Test
    void shouldMarkAllRead() {
        when(notificationMapper.markAllRead(1L)).thenReturn(3);

        notificationService.markAllRead(1L);

        verify(notificationMapper).markAllRead(1L);
    }
}
