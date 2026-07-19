package com.tianji.mall.service;

import com.tianji.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService(tempDir.toString());
    }

    @Test
    void shouldSaveFileSuccessfully() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.jpg");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});
        // mock transferTo to write to temp file
        when(file.getInputStream()).thenReturn(
                new java.io.ByteArrayInputStream(new byte[]{1, 2, 3}));

        String url = fileStorageService.saveFile(file);

        assertThat(url).startsWith("/uploads/");
        assertThat(url).endsWith(".jpg");
        // 验证文件已写入（使用transferTo，需要实际调用）
    }

    @Test
    void shouldThrowWhenFileIsEmpty() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> fileStorageService.saveFile(file))
                .isInstanceOf(BizException.class)
                .hasMessage("文件不能为空");
    }
}
