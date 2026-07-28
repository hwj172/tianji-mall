package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.mall.entity.BrowsingHistory;
import com.tianji.mall.mapper.BrowsingHistoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BrowsingHistoryService extends ServiceImpl<BrowsingHistoryMapper, BrowsingHistory> {

    private static final int MAX_HISTORY = 50;

    /**
     * 记录浏览足迹。同一用户同一商品，更新时间戳（UPSERT 逻辑）。
     */
    @Transactional
    public void recordView(Long userId, Long productId) {
        // 先删旧记录再插入，实现 UPSERT
        LambdaQueryWrapper<BrowsingHistory> wrapper = new LambdaQueryWrapper<BrowsingHistory>()
                .eq(BrowsingHistory::getUserId, userId)
                .eq(BrowsingHistory::getProductId, productId);
        remove(wrapper);

        BrowsingHistory bh = new BrowsingHistory();
        bh.setUserId(userId);
        bh.setProductId(productId);
        bh.setCreateTime(LocalDateTime.now());
        save(bh);

        // 超过最大条数时删除最旧的
        long count = count(new LambdaQueryWrapper<BrowsingHistory>()
                .eq(BrowsingHistory::getUserId, userId));
        if (count > MAX_HISTORY) {
            List<BrowsingHistory> oldest = list(new LambdaQueryWrapper<BrowsingHistory>()
                    .eq(BrowsingHistory::getUserId, userId)
                    .orderByAsc(BrowsingHistory::getCreateTime)
                    .last("LIMIT " + (count - MAX_HISTORY)));
            for (BrowsingHistory old : oldest) {
                removeById(old.getId());
            }
        }
    }

    public List<BrowsingHistory> getHistory(Long userId) {
        return list(new LambdaQueryWrapper<BrowsingHistory>()
                .eq(BrowsingHistory::getUserId, userId)
                .orderByDesc(BrowsingHistory::getCreateTime)
                .last("LIMIT " + MAX_HISTORY));
    }

    public void clearHistory(Long userId) {
        baseMapper.clearAll(userId);
    }
}
