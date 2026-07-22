package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.mall.entity.LogisticsTrack;
import com.tianji.mall.mapper.LogisticsTrackMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class LogisticsService extends ServiceImpl<LogisticsTrackMapper, LogisticsTrack> {

    private static final String[] CITIES = {"广州", "深圳", "杭州", "北京", "上海", "武汉", "成都", "南京"};
    private static final String[] COURIERS = {"张三", "李四", "王五", "赵六"};
    private static final String[] COURIER_PHONES = {"13800001111", "13900002222", "13600003333", "13700004444"};

    /**
     * 发货时生成模拟物流轨迹（6个节点，时间均匀分布在未来28小时内）。
     */
    public void generateTracks(Long orderId) {
        LocalDateTime now = LocalDateTime.now();
        String originCity = randomCity();
        String destCity = randomCity();
        int courierIdx = ThreadLocalRandom.current().nextInt(COURIERS.length);

        Object[][] templates = {
                {"PICKED_UP", "快递员已揽收", originCity + "市", now.plusHours(1)},
                {"IN_TRANSIT", "快件已到达" + originCity + "转运中心", originCity + "市", now.plusHours(4)},
                {"IN_TRANSIT", "快件已从" + originCity + "转运中心发出", originCity + "市", now.plusHours(8)},
                {"OUT_FOR_DELIVERY", "快件已到达" + destCity + "配送站", destCity + "市", now.plusHours(24)},
                {"OUT_FOR_DELIVERY", "快递员 " + COURIERS[courierIdx] + " 正在为您派送，联系电话 " + COURIER_PHONES[courierIdx], destCity + "市", now.plusHours(26)},
                {"DELIVERED", "快件已签收，感谢使用" + randomCompany(), destCity + "市", now.plusHours(28)},
        };

        for (Object[] t : templates) {
            LogisticsTrack track = new LogisticsTrack();
            track.setOrderId(orderId);
            track.setStatus((String) t[0]);
            track.setDescription((String) t[1]);
            track.setLocation((String) t[2]);
            track.setTrackTime((LocalDateTime) t[3]);
            save(track);
        }

        log.info("物流轨迹已生成: orderId={}, 节点数={}", orderId, templates.length);
    }

    /**
     * 查询订单物流轨迹，按时间升序排列。
     */
    public List<LogisticsTrack> getTracks(Long orderId) {
        return list(new LambdaQueryWrapper<LogisticsTrack>()
                .eq(LogisticsTrack::getOrderId, orderId)
                .orderByAsc(LogisticsTrack::getTrackTime));
    }

    private String randomCity() {
        return CITIES[ThreadLocalRandom.current().nextInt(CITIES.length)];
    }

    private String randomCompany() {
        String[] companies = {"顺丰速运", "中通快递", "圆通速递", "韵达快递", "申通快递"};
        return companies[ThreadLocalRandom.current().nextInt(companies.length)];
    }
}
