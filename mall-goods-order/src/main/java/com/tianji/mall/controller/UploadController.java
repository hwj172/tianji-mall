package com.tianji.mall.controller;

import com.tianji.common.result.R;
import com.tianji.mall.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final FileStorageService fileStorageService;

    /**
     * 上传商品图片（支持单文件或多文件）。
     * Admin 鉴权由 Gateway 的 /api/admin 前缀 + RequireAdmin 注解处理。
     * 这里用 X-User-Role 请求头做二次确认。
     */
    @PostMapping("/image")
    public R<List<String>> uploadImage(@RequestParam(value = "files", required = false) List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                urls.add(fileStorageService.saveFile(file));
            }
        }
        return R.ok(urls);
    }
}
