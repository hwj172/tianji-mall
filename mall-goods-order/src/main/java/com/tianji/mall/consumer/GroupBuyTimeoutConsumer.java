package com.tianji.mall.consumer;

import com.tianji.mall.entity.GroupBuyOrder;
import com.tianji.mall.entity.GroupBuyParticipant;
import com.tianji.mall.mapper.GroupBuyOrderMapper;
import com.tianji.mall.mapper.GroupBuyParticipantMapper;
import com.tianji.mall.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "group-buy-topic", consumerGroup = "group-buy-timeout-consumer",
        maxReconsumeTimes = 3)
public class GroupBuyTimeoutConsumer implements RocketMQListener<String> {

    private final GroupBuyOrderMapper groupBuyOrderMapper;
    private final GroupBuyParticipantMapper participantMapper;
    private final OrderService orderService;

    @Override
    public void onMessage(String groupOrderId) {
        Long id = Long.parseLong(groupOrderId);
        GroupBuyOrder gbo = groupBuyOrderMapper.selectById(id);
        if (gbo == null || !"OPEN".equals(gbo.getStatus())) return;
        if (gbo.getExpireTime().isBefore(LocalDateTime.now())) {
            gbo.setStatus("FAIL");
            groupBuyOrderMapper.updateById(gbo);
            log.info("拼团超时失败: groupId={}", gbo.getGroupId());

            // 取消关联的所有参团订单
            List<GroupBuyParticipant> participants = participantMapper.selectByGroupBuyOrderId(id);
            for (GroupBuyParticipant p : participants) {
                orderService.cancelOrderByTimeout(p.getOrderId());
                log.info("拼团超时取消订单: orderId={}, gboId={}", p.getOrderId(), id);
            }
        }
    }
}
