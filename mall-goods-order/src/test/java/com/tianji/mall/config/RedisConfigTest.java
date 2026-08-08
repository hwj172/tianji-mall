package com.tianji.mall.config;

import com.tianji.mall.dto.RecommendResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缓存序列化 round-trip 测试。
 * GenericJackson2JsonRedisSerializer 带自定义 ObjectMapper 时必须启用类型信息，
 * 否则缓存 List<POJO> 反序列化退化为 LinkedHashMap（热销榜 ClassCastException 的根因）。
 */
class RedisConfigTest {

    @Test
    void cachedListOfPojoRoundTripsWithoutTypeLoss() {
        RedisCacheConfiguration cfg = new RedisConfig().cacheConfiguration();
        RedisSerializationContext.SerializationPair<Object> pair = cfg.getValueSerializationPair();

        // 生产路径 Collectors.toList() 返回 ArrayList（非 final），NON_FINAL 类型信息才生效；
        // 不可变集合（List.of 的 ImmutableListN 是 final）无法被 GenericJackson 还原，禁止缓存
        List<RecommendResponse.RecommendItem> items = new java.util.ArrayList<>(List.of(
                new RecommendResponse.RecommendItem(1L, "商品A", new BigDecimal("99.00"), 5L, "热销", "[\"http://img/a\"]"),
                new RecommendResponse.RecommendItem(2L, "商品B", new BigDecimal("199.00"), 3L, "", null)));

        java.nio.ByteBuffer buffer = pair.getWriter().write(items);
        Object result = pair.getReader().read(buffer);

        assertTrue(result instanceof List,
                "反序列化结果应为 List，实际: " + (result == null ? "null" : result.getClass().getName()));
        List<?> list = (List<?>) result;
        assertEquals(2, list.size());
        assertTrue(list.get(0) instanceof RecommendResponse.RecommendItem,
                "元素类型丢失（退化为 LinkedHashMap），实际: " + list.get(0).getClass().getName());
        RecommendResponse.RecommendItem first = (RecommendResponse.RecommendItem) list.get(0);
        assertEquals(1L, first.getId());
        assertEquals("商品A", first.getName());
        assertEquals(new BigDecimal("99.00"), first.getPrice());
        assertEquals("热销", first.getReason());
    }
}
