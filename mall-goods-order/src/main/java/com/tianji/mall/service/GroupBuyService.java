package com.tianji.mall.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.GroupBuyActivityRequest;
import com.tianji.mall.dto.GroupBuyDetailResponse;
import com.tianji.mall.dto.GroupBuyTier;
import com.tianji.mall.entity.GroupBuy;
import com.tianji.mall.entity.GroupBuyOrder;
import com.tianji.mall.entity.Product;
import com.tianji.mall.mapper.GroupBuyMapper;
import com.tianji.mall.mapper.GroupBuyOrderMapper;
import com.tianji.mall.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupBuyService {

    private final GroupBuyMapper groupBuyMapper;
    private final GroupBuyOrderMapper groupBuyOrderMapper;
    private final ProductMapper productMapper;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 管理端 ====================

    public GroupBuy createActivity(GroupBuyActivityRequest req) {
        Product product = productMapper.selectById(req.getProductId());
        if (product == null) throw new BizException("商品不存在");
        if (groupBuyMapper.selectByProductId(req.getProductId()) != null) {
            throw new BizException("该商品已有进行中的拼团活动");
        }
        if (product.getSeckillPrice() != null) {
            throw new BizException("该商品正在参与秒杀，不能设置拼团");
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
            throw new BizException("拼团阶梯配置格式异常");
        }
    }

    public void updateActivity(Long activityId, GroupBuyActivityRequest req) {
        GroupBuy gb = groupBuyMapper.selectById(activityId);
        if (gb == null) throw new BizException("拼团活动不存在");
        if (req.getExpireHours() != null && req.getExpireHours() <= 0) {
            throw new BizException("过期小时数必须大于0");
        }
        try {
            gb.setTiers(objectMapper.writeValueAsString(req.getTiers()));
        } catch (JsonProcessingException e) {
            throw new BizException("拼团阶梯配置格式异常");
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
        if (activity == null) throw new BizException("拼团活动不存在");

        List<GroupBuyOrder> openGroups = groupBuyOrderMapper.selectOpenByProductId(activity.getProductId());
        List<GroupBuyTier> tiers = parseTiers(activity.getTiers());

        return new GroupBuyDetailResponse(activity, openGroups, tiers);
    }

    @Transactional
    public GroupBuyOrder startGroup(Long userId, Long activityId, int targetCount, Long addressId) {
        GroupBuy activity = groupBuyMapper.selectById(activityId);
        if (activity == null || activity.getStatus() != 1) throw new BizException("拼团活动不存在或已结束");

        List<GroupBuyTier> tiers = parseTiers(activity.getTiers());
        tiers.stream().filter(t -> t.getCount().equals(targetCount)).findFirst()
                .orElseThrow(() -> new BizException("不支持的拼团人数"));

        String groupId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        GroupBuyOrder gbo = new GroupBuyOrder();
        gbo.setGroupId(groupId);
        gbo.setProductId(activity.getProductId());
        gbo.setTargetTier(targetCount);
        gbo.setCurrentCount(1);
        gbo.setStatus("OPEN");
        gbo.setExpireTime(LocalDateTime.now().plusHours(activity.getExpireHours()));
        groupBuyOrderMapper.insert(gbo);

        return gbo;
    }

    @Transactional
    public void joinGroup(String groupId, Long userId) {
        GroupBuyOrder gbo = groupBuyOrderMapper.selectByGroupIdForUpdate(groupId);
        if (gbo == null || !"OPEN".equals(gbo.getStatus())) throw new BizException("团不存在或已结束");
        if (gbo.getExpireTime().isBefore(LocalDateTime.now())) {
            gbo.setStatus("FAIL");
            groupBuyOrderMapper.updateById(gbo);
            throw new BizException("团已过期");
        }

        int affected = groupBuyOrderMapper.incrementCount(gbo.getId());
        if (affected == 0) throw new BizException("团已满员");
    }

    public List<GroupBuyOrder> getMyGroups(Long userId) {
        return groupBuyOrderMapper.selectAllOpen();
    }

    // ==================== 辅助 ====================

    public List<GroupBuyTier> parseTiers(String tiersJson) {
        try {
            return objectMapper.readValue(tiersJson, new TypeReference<List<GroupBuyTier>>() {});
        } catch (Exception e) {
            throw new BizException("拼团阶梯配置异常");
        }
    }
}
