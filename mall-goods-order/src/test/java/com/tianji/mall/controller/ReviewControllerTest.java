package com.tianji.mall.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.ReviewCreateRequest;
import com.tianji.mall.dto.ReviewResponse;
import com.tianji.mall.feign.AiChatFeignClient;
import com.tianji.mall.feign.PayFeignClient;
import com.tianji.mall.service.ReviewService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private RocketMQTemplate rocketMQTemplate;

    @MockBean
    private AiChatFeignClient aiChatFeignClient;

    @MockBean
    private PayFeignClient payFeignClient;

    @Test
    void shouldCreateReview() throws Exception {
        when(jwtUtil.getUserId("token123")).thenReturn(100L);
        doNothing().when(reviewService).createReview(eq(100L), any(ReviewCreateRequest.class));

        ReviewCreateRequest req = new ReviewCreateRequest();
        req.setOrderId(1L);
        req.setProductId(10L);
        req.setRating(5);
        req.setContent("很好");

        mockMvc.perform(post("/api/review")
                        .header("Authorization", "Bearer token123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn400OnMissingRating() throws Exception {
        when(jwtUtil.getUserId("token123")).thenReturn(100L);

        String body = "{\"orderId\":1,\"productId\":10}";

        mockMvc.perform(post("/api/review")
                        .header("Authorization", "Bearer token123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400OnInvalidRating() throws Exception {
        when(jwtUtil.getUserId("token123")).thenReturn(100L);

        String body = "{\"orderId\":1,\"productId\":10,\"rating\":0}";

        mockMvc.perform(post("/api/review")
                        .header("Authorization", "Bearer token123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetProductReviews() throws Exception {
        ReviewResponse resp = new ReviewResponse();
        resp.setId(1L);
        resp.setRating(5);
        resp.setContent("好评");
        when(reviewService.getProductReviews(10L, 1, 20)).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/review/product/10")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].rating").value(5))
                .andExpect(jsonPath("$.data[0].content").value("好评"));
    }

    @Test
    void shouldGetMyReviews() throws Exception {
        when(jwtUtil.getUserId("token123")).thenReturn(100L);
        ReviewResponse resp = new ReviewResponse();
        resp.setId(1L);
        resp.setUserId(100L);
        resp.setRating(4);
        when(reviewService.getMyReviews(100L, 1, 20)).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/review/my")
                        .header("Authorization", "Bearer token123")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].rating").value(4));
    }
}
