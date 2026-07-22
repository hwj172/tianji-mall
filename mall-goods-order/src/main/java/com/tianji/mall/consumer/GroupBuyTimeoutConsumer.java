package com.tianji.mall.consumer;

import com.tianji.mall.entity.GroupBuyOrder;
import com.tianji.mall.mapper.GroupBuyOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "group-buy-topic", consumerGroup = "group-buy-timeout-consumer")
public class GroupBuyTimeoutConsumer implements RocketMQListener<String> {

    private final GroupBuyOrderMapper groupBuyOrderMapper;

    @Override
    public void onMessage(String groupOrderId) {
        Long id = Long.parseLong(groupOrderId);
        GroupBuyOrder gbo = groupBuyOrderMapper.selectById(id);
        if (gbo == null || !"OPEN".equals(gbo.getStatus())) return;
        if (gbo.getExpireTime().isBefore(LocalDateTime.now())) {
            gbo.setStatus("FAIL");
            groupBuyOrderMapper.updateById(gbo);
            log.info("拼团超时失败: groupId={}", gbo.getGroupId());
        }
    }
}
