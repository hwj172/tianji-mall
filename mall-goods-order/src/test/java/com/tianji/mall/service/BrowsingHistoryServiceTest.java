package com.tianji.mall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.mall.entity.BrowsingHistory;
import com.tianji.mall.mapper.BrowsingHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrowsingHistoryServiceTest {

    @Mock
    private BrowsingHistoryMapper browsingHistoryMapper;

    private BrowsingHistoryService browsingHistoryService;

    @BeforeEach
    void setUp() {
        browsingHistoryService = new BrowsingHistoryService();
        ReflectionTestUtils.setField(browsingHistoryService, "baseMapper", browsingHistoryMapper);
    }

    @Test
    void shouldRecordView() {
        // first remove old, then save
        when(browsingHistoryMapper.delete(any())).thenReturn(1);
        when(browsingHistoryMapper.insert(any(BrowsingHistory.class))).thenReturn(1);
        // count returns <= MAX (50), so no cleanup
        when(browsingHistoryMapper.selectCount(any())).thenReturn(10L);

        browsingHistoryService.recordView(1L, 100L);

        ArgumentCaptor<BrowsingHistory> captor = ArgumentCaptor.forClass(BrowsingHistory.class);
        verify(browsingHistoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getProductId()).isEqualTo(100L);
    }

    @Test
    void shouldGetHistoryPaged() {
        when(browsingHistoryMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<BrowsingHistory> p = inv.getArgument(0);
            p.setRecords(List.of());
            p.setTotal(0);
            return p;
        });

        Page<BrowsingHistory> result = browsingHistoryService.getHistory(1L, 1, 20);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
        verify(browsingHistoryMapper).selectPage(any(), any());
    }

    @Test
    void shouldClearHistory() {
        when(browsingHistoryMapper.clearAll(1L)).thenReturn(5);

        browsingHistoryService.clearHistory(1L);

        verify(browsingHistoryMapper).clearAll(1L);
    }
}
