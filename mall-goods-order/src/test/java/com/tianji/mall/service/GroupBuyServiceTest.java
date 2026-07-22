package com.tianji.mall.service;

import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.GroupBuyActivityRequest;
import com.tianji.mall.dto.GroupBuyTier;
import com.tianji.mall.entity.GroupBuy;
import com.tianji.mall.entity.GroupBuyOrder;
import com.tianji.mall.entity.Product;
import com.tianji.mall.mapper.GroupBuyMapper;
import com.tianji.mall.mapper.GroupBuyOrderMapper;
import com.tianji.mall.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupBuyServiceTest {

    @Mock private GroupBuyMapper groupBuyMapper;
    @Mock private GroupBuyOrderMapper groupBuyOrderMapper;
    @Mock private ProductMapper productMapper;

    private GroupBuyService groupBuyService;
    private Product product;
    private GroupBuyActivityRequest req;

    @BeforeEach
    void setUp() {
        groupBuyService = new GroupBuyService(groupBuyMapper, groupBuyOrderMapper, productMapper);
        product = buildProduct(1L, "iPhone", 100);
        req = buildActivityRequest(1L);
    }

    // 1. 创建拼团活动成功
    @Test
    void shouldCreateActivity() {
        when(productMapper.selectById(1L)).thenReturn(product);
        when(groupBuyMapper.selectByProductId(1L)).thenReturn(null);

        GroupBuy result = groupBuyService.createActivity(req);

        assertThat(result).isNotNull();
        assertThat(result.getProductId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(1);
        verify(groupBuyMapper).selectByProductId(1L);
    }

    // 2. 重复创建拒绝
    @Test
    void shouldRejectDuplicateActivity() {
        when(productMapper.selectById(1L)).thenReturn(product);
        when(groupBuyMapper.selectByProductId(1L)).thenReturn(new GroupBuy());

        assertThatThrownBy(() -> groupBuyService.createActivity(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已有进行中的拼团活动");
    }

    // 3. 秒杀商品不能拼团
    @Test
    void shouldRejectSeckillProduct() {
        product.setSeckillPrice(BigDecimal.valueOf(1999));
        when(productMapper.selectById(1L)).thenReturn(product);

        assertThatThrownBy(() -> groupBuyService.createActivity(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("秒杀");
    }

    // 4. 获取活动列表
    @Test
    void shouldGetActiveActivities() {
        when(groupBuyMapper.selectActive()).thenReturn(List.of(new GroupBuy()));

        assertThat(groupBuyService.getActiveActivities()).hasSize(1);
    }

    // 5. 开团
    @Test
    void shouldStartGroup() {
        GroupBuy gb = buildGroupBuy(1L);
        when(groupBuyMapper.selectById(1L)).thenReturn(gb);

        GroupBuyOrder result = groupBuyService.startGroup(1L, 1L, 5, 1L);

        assertThat(result.getGroupId()).isNotNull();
        assertThat(result.getGroupId()).hasSize(8);
        assertThat(result.getStatus()).isEqualTo("OPEN");
        assertThat(result.getTargetTier()).isEqualTo(5);
    }

    // 6. 参团
    @Test
    void shouldJoinGroup() {
        GroupBuyOrder gbo = buildGroupBuyOrder(1L, "abc123", 5, 3, "OPEN");
        when(groupBuyOrderMapper.selectByGroupIdForUpdate("abc123")).thenReturn(gbo);
        when(groupBuyOrderMapper.incrementCount(gbo.getId())).thenReturn(1);

        groupBuyService.joinGroup("abc123", 2L);

        verify(groupBuyOrderMapper).incrementCount(gbo.getId());
    }

    // 7. 已满员团拒绝参团
    @Test
    void shouldRejectFullGroup() {
        GroupBuyOrder gbo = buildGroupBuyOrder(1L, "abc123", 5, 3, "OPEN");
        when(groupBuyOrderMapper.selectByGroupIdForUpdate("abc123")).thenReturn(gbo);
        when(groupBuyOrderMapper.incrementCount(gbo.getId())).thenReturn(0);

        assertThatThrownBy(() -> groupBuyService.joinGroup("abc123", 2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已满员");
    }

    // ==================== helpers ====================

    private Product buildProduct(Long id, String name, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(BigDecimal.valueOf(6999));
        p.setStock(stock);
        p.setStatus(1);
        return p;
    }

    private GroupBuyActivityRequest buildActivityRequest(Long productId) {
        GroupBuyActivityRequest r = new GroupBuyActivityRequest();
        r.setProductId(productId);
        r.setTiers(List.of(new GroupBuyTier(3, BigDecimal.valueOf(0.9)), new GroupBuyTier(5, BigDecimal.valueOf(0.8))));
        r.setStartTime(LocalDateTime.now());
        r.setEndTime(LocalDateTime.now().plusDays(7));
        return r;
    }

    private GroupBuy buildGroupBuy(Long id) {
        GroupBuy gb = new GroupBuy();
        gb.setId(id);
        gb.setProductId(1L);
        gb.setTiers("[{\"count\":3,\"discount\":0.9},{\"count\":5,\"discount\":0.8}]");
        gb.setExpireHours(24);
        gb.setStatus(1);
        return gb;
    }

    private GroupBuyOrder buildGroupBuyOrder(Long id, String groupId, int target, int current, String status) {
        GroupBuyOrder gbo = new GroupBuyOrder();
        gbo.setId(id);
        gbo.setGroupId(groupId);
        gbo.setTargetTier(target);
        gbo.setCurrentCount(current);
        gbo.setStatus(status);
        gbo.setExpireTime(LocalDateTime.now().plusHours(20));
        return gbo;
    }
}
