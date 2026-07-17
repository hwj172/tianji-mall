package com.tianji.mall.controller;

import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.AddressRequest;
import com.tianji.mall.entity.Address;
import com.tianji.mall.service.AddressService;
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
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AddressService addressService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private org.redisson.api.RedissonClient redissonClient;

    @BeforeEach
    void setUp() {
        when(jwtUtil.getUserId(anyString())).thenReturn(1L);
    }

    // ==================== GET /api/address/list ====================

    @Test
    void shouldGetAddressList() throws Exception {
        Address addr = buildAddress(1L, "张三", "13800000001");
        when(addressService.getAddressList(1L)).thenReturn(List.of(addr));

        mockMvc.perform(get("/api/address/list")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].receiverName").value("张三"))
                .andExpect(jsonPath("$.data[0].phone").value("13800000001"));
    }

    // ==================== POST /api/address ====================

    @Test
    void shouldAddAddress() throws Exception {
        doNothing().when(addressService).addAddress(eq(1L), any(AddressRequest.class));

        mockMvc.perform(post("/api/address")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"receiverName\":\"张三\"," +
                                "\"phone\":\"13800000001\"," +
                                "\"province\":\"广东省\"," +
                                "\"city\":\"深圳市\"," +
                                "\"district\":\"南山区\"," +
                                "\"detail\":\"科技园路1号\"," +
                                "\"isDefault\":1" +
                                "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== PUT /api/address/{id} ====================

    @Test
    void shouldUpdateAddress() throws Exception {
        doNothing().when(addressService).updateAddress(eq(1L), eq(10L), any(AddressRequest.class));

        mockMvc.perform(put("/api/address/10")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"receiverName\":\"李四\"," +
                                "\"phone\":\"13900000002\"," +
                                "\"province\":\"北京市\"," +
                                "\"city\":\"北京市\"," +
                                "\"district\":\"海淀区\"," +
                                "\"detail\":\"中关村\"," +
                                "\"isDefault\":0" +
                                "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== DELETE /api/address/{id} ====================

    @Test
    void shouldDeleteAddress() throws Exception {
        doNothing().when(addressService).deleteAddress(1L, 10L);

        mockMvc.perform(delete("/api/address/10")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== exception scenarios ====================

    @Test
    void shouldReturnErrorWhenMissingAuthHeader() throws Exception {
        mockMvc.perform(get("/api/address/list"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ==================== helpers ====================

    private Address buildAddress(Long id, String name, String phone) {
        Address addr = new Address();
        addr.setId(id);
        addr.setUserId(1L);
        addr.setReceiverName(name);
        addr.setPhone(phone);
        addr.setProvince("广东省");
        addr.setCity("深圳市");
        addr.setDistrict("南山区");
        addr.setDetail("科技园路1号");
        addr.setIsDefault(1);
        return addr;
    }
}
