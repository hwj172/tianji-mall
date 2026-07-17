package com.tianji.mall.controller;

import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.CartAddRequest;
import com.tianji.mall.dto.CartCheckRequest;
import com.tianji.mall.dto.CartUpdateRequest;
import com.tianji.mall.entity.CartItem;
import com.tianji.mall.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartService cartService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private org.redisson.api.RedissonClient redissonClient;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @BeforeEach
    void setUp() {
        when(jwtUtil.getUserId(anyString())).thenReturn(1L);
    }

    // ==================== GET /api/cart/list ====================

    @Test
    void shouldGetCartList() throws Exception {
        CartItem item = buildCartItem(1L, 100L, 2);
        when(cartService.getCartList(1L)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/cart/list")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].productId").value(100))
                .andExpect(jsonPath("$.data[0].quantity").value(2));
    }

    // ==================== POST /api/cart/add ====================

    @Test
    void shouldAddCartItem() throws Exception {
        doNothing().when(cartService).addItem(eq(1L), any(CartAddRequest.class));

        mockMvc.perform(post("/api/cart/add")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":100,\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldRejectInvalidCartAdd() throws Exception {
        // quantity 是 null，触发 @NotNull
        mockMvc.perform(post("/api/cart/add")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== PUT /api/cart/{id} ====================

    @Test
    void shouldUpdateQuantity() throws Exception {
        doNothing().when(cartService).updateQuantity(1L, 10L, 5);

        mockMvc.perform(put("/api/cart/10")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== DELETE /api/cart/{id} ====================

    @Test
    void shouldDeleteCartItem() throws Exception {
        doNothing().when(cartService).deleteItem(1L, 10L);

        mockMvc.perform(delete("/api/cart/10")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== PUT /api/cart/check ====================

    @Test
    void shouldCheckCartItem() throws Exception {
        doNothing().when(cartService).checkItem(1L, 10L, 1);

        mockMvc.perform(put("/api/cart/check")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemId\":10,\"checked\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== internal endpoints ====================

    @Test
    void shouldGetCartListInternal() throws Exception {
        CartItem item = buildCartItem(1L, 100L, 2);
        when(cartService.getCartList(1L)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/cart/internal/list").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productId").value(100));
    }

    @Test
    void shouldAddCartItemInternal() throws Exception {
        doNothing().when(cartService).addItem(eq(1L), any(CartAddRequest.class));

        mockMvc.perform(post("/api/cart/internal/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"productId\":100,\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== exception scenarios ====================

    @Test
    void shouldReturnErrorWhenMissingAuthHeader() throws Exception {
        mockMvc.perform(get("/api/cart/list"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ==================== helpers ====================

    private CartItem buildCartItem(Long id, Long productId, int quantity) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setUserId(1L);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setChecked(1);
        return item;
    }
}
