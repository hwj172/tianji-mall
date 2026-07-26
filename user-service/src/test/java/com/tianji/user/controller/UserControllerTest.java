package com.tianji.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.util.JwtUtil;
import com.tianji.user.dto.LoginResponse;
import com.tianji.user.entity.User;
import com.tianji.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        when(jwtUtil.getUserId(anyString())).thenReturn(1L);
    }

    // ==================== POST /api/user/register ====================

    @Test
    void shouldRegisterSuccessfully() throws Exception {
        doNothing().when(userService).register(any());

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldRejectInvalidRegister() throws Exception {
        // username 长度最小 3，这里发 2 个字符
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"password\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== POST /api/user/login ====================

    @Test
    void shouldLoginSuccessfully() throws Exception {
        LoginResponse resp = new LoginResponse(1L, "testuser", "mock-jwt-token", "user");
        when(userService.login("testuser", "123456")).thenReturn(resp);

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.token").value("mock-jwt-token"));
    }

    // ==================== GET /api/user/info ====================

    @Test
    void shouldGetUserInfo() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPhone("13800000001");
        user.setEmail("test@example.com");
        user.setStatus(1);
        when(userService.getUserById(1L)).thenReturn(user);

        mockMvc.perform(get("/api/user/info")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.phone").value("13800000001"));
    }

    // ==================== exception scenarios ====================

    @Test
    void shouldReturnErrorWhenMissingAuthHeader() throws Exception {
        mockMvc.perform(get("/api/user/info"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ==================== PUT /api/user/profile ====================

    @Test
    void shouldUpdateProfile() throws Exception {
        mockMvc.perform(put("/api/user/profile")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newname\",\"phone\":\"13900001111\",\"email\":\"new@test.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldRejectInvalidProfile() throws Exception {
        // username 为空字符串（@Size(min=1) 不通过）
        mockMvc.perform(put("/api/user/profile")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"phone\":\"139\",\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== PUT /api/user/avatar ====================

    @Test
    void shouldUpdateAvatar() throws Exception {
        when(userService.updateAvatar(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenReturn("/uploads/avatar_abc.jpg");

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/user/avatar")
                        .file(file)
                        .header("Authorization", "Bearer test-token")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("/uploads/avatar_abc.jpg"));
    }

    // ==================== PUT /api/user/password ====================

    @Test
    void shouldUpdatePassword() throws Exception {
        mockMvc.perform(put("/api/user/password")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"old123456\",\"newPassword\":\"new123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldRejectShortNewPassword() throws Exception {
        mockMvc.perform(put("/api/user/password")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"old\",\"newPassword\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== internal endpoints ====================

    @Test
    void shouldListUsersInternal() throws Exception {
        Page<User> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(userService.listUsers(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/user/internal/list")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void shouldUpdateUserStatusInternal() throws Exception {
        mockMvc.perform(put("/api/user/internal/1/status")
                        .param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
