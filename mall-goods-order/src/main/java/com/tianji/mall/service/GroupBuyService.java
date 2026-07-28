package com.tianji.mall.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianji.common.exception.BizErrorCode;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.GroupBuyActivityRequest;
import com.tianji.mall.dto.GroupBuyDetailResponse;
import com.tianji.mall.dto.GroupBuyTier;
import com.tianji.mall.dto.OrderCreateRequest;
import com.tianji.mall.entity.GroupBuy;
import com.tianji.mall.entity.GroupBuyOrder;
import com.tianji.mall.entity.GroupBuyParticipant;
import com.tianji.mall.entity.Order;
import com.tianji.mall.entity.Product;
import com.tianji.mall.mapper.GroupBuyMapper;
import com.tianji.mall.mapper.GroupBuyOrderMapper;
import com.tianji.mall.mapper.GroupBuyParticipantMapper;
import com.tianji.mall.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupBuyService {

    private final GroupBuyMapper groupBuyMapper;
    private final GroupBuyOrderMapper groupBuyOrderMapper;
    private final GroupBuyParticipantMapper participantMapper;
    private final ProductMapper productMapper;
    private final OrderService orderService;
    private final RocketMQTemplate rocketMQTemplate;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 管理端 ====================

    public GroupBuy createActivity(GroupBuyActivityRequest req) {
        Product product = productMapper.selectById(req.getProductId());
        if (product == null) throw new BizException(BizErrorCode.PRODUCT_NOT_FOUND);
        if (groupBuyMapper.selectByProductId(req.getProductId()) != null) {
            throw new BizException(BizErrorCode.GROUP_BUY_DUPLICATE);
        }
        if (product.getSeckillPrice() != null) {
            throw new BizException(BizErrorCode.GROUP_BUY_SECKILL_CONFLICT);
        }

        try {
            GroupBuy gb = new GroupBuy();
            gb.setProductId(req.getProductId());
            gb.setTiers(objectMapper.writeValueAsString(req.getTiers()));
            gb.setStartTime(req.getStartTime());
            gb.setEndTime(req.getEndTime());
            gb.setExpireHours(req.getExpireHours());
            gb.setStatus(1);
            groupBuyMapper.insert(gb);
            return gb;
        } catch (JsonProcessingException e) {
            throw new BizException(BizErrorCode.GROUP_BUY_TIER_ERROR);
        }
    }

    public void updateActivity(Long activityId, GroupBuyActivityRequest req) {
        GroupBuy gb = groupBuyMapper.selectById(activityId);
        if (gb == null) throw new BizException(BizErrorCode.GROUP_BUY_NOT_FOUND);
        if (req.getExpireHours() != null && req.getExpireHours() <= 0) {
            throw new BizException(BizErrorCode.GROUP_BUY_INVALID_HOURS);
        }
        try {
            gb.setTiers(objectMapper.writeValueAsString(req.getTiers()));
        } catch (JsonProcessingException e) {
            throw new BizException(BizErrorCode.GROUP_BUY_TIER_ERROR);
        }
        gb.setStartTime(req.getStartTime());
        gb.setEndTime(req.getEndTime());
        gb.setExpireHours(req.getExpireHours());
        groupBuyMapper.updateById(gb);
    }

    // ==================== 用户端 ====================

    public List<GroupBuy> getActiveActivities() {
        return groupBuyMapper.selectActive();
    }

    public GroupBuyDetailResponse getDetail(Long activityId) {
        GroupBuy activity = groupBuyMapper.selectById(activityId);
        if (activity == null) throw new BizException(BizErrorCode.GROUP_BUY_NOT_FOUND);

        List<GroupBuyOrder> openGroups = groupBuyOrderMapper.selectOpenByProductId(activity.getProductId());
        List<GroupBuyTier> tiers = parseTiers(activity.getTiers());

        return new GroupBuyDetailResponse(activity, openGroups, tiers);
    }

    @Transactional
    public Map<String, Object> startGroup(Long userId, Long activityId, int targetCount,
                                          OrderCreateRequest orderReq) {
        GroupBuy activity = groupBuyMapper.selectById(activityId);
        if (activity == null || activity.getStatus() != 1) {
            throw new BizException(BizErrorCode.GROUP_BUY_NOT_FOUND);
        }

        GroupBuyTier tier = findTier(activity, targetCount);
        BigDecimal discount = calculateDiscount(activity, tier);
        orderReq.setGroupBuyDiscount(discount);

        // 创建订单（锁+库存+coupon 全包）
        Order order = orderService.createOrder(userId, orderReq);

        // 创建团
        String groupId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        GroupBuyOrder gbo = new GroupBuyOrder();
        gbo.setGroupId(groupId);
        gbo.setUserId(userId);
        gbo.setProductId(activity.getProductId());
        gbo.setTargetTier(targetCount);
        gbo.setCurrentCount(1);
        gbo.setStatus("OPEN");
        gbo.setExpireTime(LocalDateTime.now().plusHours(activity.getExpireHours()));
        groupBuyOrderMapper.insert(gbo);

        // 记录参团
        insertParticipant(gbo.getId(), userId, order.getId());

        // 发送拼团超时延迟消息
        sendTimeoutMessage(gbo.getId(), activity.getExpireHours());

        return Map.of("orderId", order.getId(), "groupId", groupId,
                "discount", discount, "status", gbo.getStatus());
    }

    @Transactional
    public Map<String, Object> joinGroup(String groupId, Long userId, OrderCreateRequest orderReq) {
        GroupBuyOrder gbo = groupBuyOrderMapper.selectByGroupIdForUpdate(groupId);
        if (gbo == null || !"OPEN".equals(gbo.getStatus())) {
            throw new BizException(BizErrorCode.GROUP_BUY_CLOSED);
        }
        if (gbo.getExpireTime().isBefore(LocalDateTime.now())) {
            gbo.setStatus("FAIL");
            groupBuyOrderMapper.updateById(gbo);
            throw new BizException(BizErrorCode.GROUP_BUY_EXPIRED);
        }

        GroupBuy activity = groupBuyMapper.selectByProductId(gbo.getProductId());
        GroupBuyTier tier = findTier(activity, gbo.getTargetTier());
        BigDecimal discount = calculateDiscount(activity, tier);
        orderReq.setGroupBuyGroupId(groupId);
        orderReq.setGroupBuyDiscount(discount);

        // 创建订单
        Order order = orderService.createOrder(userId, orderReq);

        // 原子参团（CAS: current_count < target_tier AND status = 'OPEN'）
        int affected = groupBuyOrderMapper.incrementCount(gbo.getId());
        if (affected == 0) {
            throw new BizException(BizErrorCode.GROUP_BUY_FULL);
        }

        // 记录参团
        insertParticipant(gbo.getId(), userId, order.getId());

        // 重新读取最新状态
        gbo = groupBuyOrderMapper.selectById(gbo.getId());
        return Map.of("orderId", order.getId(), "groupId", groupId,
                "currentCount", gbo.getCurrentCount(), "status", gbo.getStatus(),
                "discount", discount);
    }

    public List<GroupBuyOrder> getMyGroups(Long userId) {
        return groupBuyOrderMapper.selectAllOpen();
    }

    // ==================== 辅助 ====================

    public List<GroupBuyTier> parseTiers(String tiersJson) {
        try {
            return objectMapper.readValue(tiersJson, new TypeReference<List<GroupBuyTier>>() {});
        } catch (Exception e) {
            throw new BizException(BizErrorCode.GROUP_BUY_TIER_ERROR);
        }
    }

    private GroupBuyTier findTier(GroupBuy activity, int targetCount) {
        return parseTiers(activity.getTiers()).stream()
                .filter(t -> java.util.Objects.equals(t.getCount(), targetCount))
                .findFirst()
                .orElseThrow(() -> new BizException("不支持的拼团人数"));
    }

    private BigDecimal calculateDiscount(GroupBuy activity, GroupBuyTier tier) {
        Product product = productMapper.selectById(activity.getProductId());
        if (product == null) throw new BizException(BizErrorCode.PRODUCT_NOT_FOUND);
        // discount 是折扣系数（0.9 = 9折），折扣金额 = 原价 × (1 - discount)
        return product.getPrice().multiply(BigDecimal.ONE.subtract(tier.getDiscount()));
    }

    private void insertParticipant(Long gboId, Long userId, Long orderId) {
        GroupBuyParticipant p = new GroupBuyParticipant();
        p.setGroupBuyOrderId(gboId);
        p.setUserId(userId);
        p.setOrderId(orderId);
        participantMapper.insert(p);
    }

    private void sendTimeoutMessage(Long gboId, int expireHours) {
        try {
            String delayLevel = expireHours <= 2 ? String.valueOf(16 + expireHours) : "18";
            Message<String> msg = MessageBuilder.withPayload(gboId.toString())
                    .setHeader("DELAY", delayLevel)
                    .build();
            rocketMQTemplate.syncSend("group-buy-topic:TIMEOUT_CHECK", msg, 3000);
            log.info("拼团超时消息已发送: gboId={}, expireHours={}, delayLevel={}", gboId, expireHours, delayLevel);
        } catch (Exception e) {
            log.error("发送拼团超时消息失败: gboId={}", gboId, e);
        }
    }
}
