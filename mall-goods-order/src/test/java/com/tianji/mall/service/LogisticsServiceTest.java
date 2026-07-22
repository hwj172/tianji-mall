package com.tianji.mall.service;

import com.tianji.mall.entity.LogisticsTrack;
import com.tianji.mall.mapper.LogisticsTrackMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogisticsServiceTest {

    @Mock
    private LogisticsTrackMapper logisticsTrackMapper;

    private LogisticsService logisticsService;

    @BeforeEach
    void setUp() {
        logisticsService = new LogisticsService();
        ReflectionTestUtils.setField(logisticsService, "baseMapper", logisticsTrackMapper);
    }

    @Test
    void shouldGenerateSixTracks() {
        logisticsService.generateTracks(1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LogisticsTrack> captor = ArgumentCaptor.forClass(LogisticsTrack.class);
        verify(logisticsTrackMapper, times(6)).insert(captor.capture());

        List<LogisticsTrack> tracks = captor.getAllValues();
        assertThat(tracks).hasSize(6);
        assertThat(tracks.get(0).getStatus()).isEqualTo("PICKED_UP");
        assertThat(tracks.get(0).getOrderId()).isEqualTo(1L);
        assertThat(tracks.get(5).getStatus()).isEqualTo("DELIVERED");
        // 时间递增
        for (int i = 1; i < tracks.size(); i++) {
            assertThat(tracks.get(i).getTrackTime())
                    .isAfterOrEqualTo(tracks.get(i - 1).getTrackTime());
        }
    }

    @Test
    void shouldGetTracksOrderedByTime() {
        LogisticsTrack t1 = buildTrack(1L, "PICKED_UP", java.time.LocalDateTime.now().minusHours(2));
        LogisticsTrack t2 = buildTrack(1L, "DELIVERED", java.time.LocalDateTime.now());
        when(logisticsTrackMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenReturn(List.of(t1, t2));

        List<LogisticsTrack> result = logisticsService.getTracks(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStatus()).isEqualTo("PICKED_UP");
        assertThat(result.get(1).getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void shouldReturnEmptyForNoTracks() {
        when(logisticsTrackMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenReturn(List.of());

        List<LogisticsTrack> result = logisticsService.getTracks(999L);

        assertThat(result).isEmpty();
    }

    private LogisticsTrack buildTrack(Long orderId, String status, java.time.LocalDateTime time) {
        LogisticsTrack t = new LogisticsTrack();
        t.setId(1L);
        t.setOrderId(orderId);
        t.setStatus(status);
        t.setDescription("test");
        t.setTrackTime(time);
        return t;
    }
}
