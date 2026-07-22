package com.tianji.mall.controller;

import com.tianji.mall.feign.AiChatFeignClient;
import com.tianji.mall.feign.PayFeignClient;
import com.tianji.mall.service.FileStorageService;
import com.tianji.mall.service.DashboardService;
import com.tianji.mall.service.RecommendService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileStorageService fileStorageService;

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private RocketMQTemplate rocketMQTemplate;

    @MockBean
    private AiChatFeignClient aiChatFeignClient;

    @MockBean
    private PayFeignClient payFeignClient;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private RecommendService recommendService;

    @Test
    void shouldUploadSingleFile() throws Exception {
        when(fileStorageService.saveFile(any())).thenReturn("/uploads/abc123.jpg");

        MockMultipartFile file = new MockMultipartFile(
                "files", "test.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/upload/image")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("/uploads/abc123.jpg"));
    }

    @Test
    void shouldUploadMultipleFiles() throws Exception {
        when(fileStorageService.saveFile(any()))
                .thenReturn("/uploads/a.jpg", "/uploads/b.jpg");

        MockMultipartFile file1 = new MockMultipartFile(
                "files", "a.jpg", "image/jpeg", new byte[]{1});
        MockMultipartFile file2 = new MockMultipartFile(
                "files", "b.jpg", "image/jpeg", new byte[]{2});

        mockMvc.perform(multipart("/api/upload/image")
                        .file(file1)
                        .file(file2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void shouldReturnEmptyListWhenNoFiles() throws Exception {
        mockMvc.perform(multipart("/api/upload/image"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
