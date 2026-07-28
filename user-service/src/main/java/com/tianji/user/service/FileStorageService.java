package com.tianji.user.service;

import com.tianji.common.exception.BizErrorCode;
import com.tianji.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private final Path uploadDir;

    public FileStorageService(@Value("${file.upload-dir:./uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new BizException(BizErrorCode.UPLOAD_DIR_FAILED, this.uploadDir.toString());
        }
    }

    /**
     * 保存上传文件到本地磁盘，返回访问 URL。
     */
    public String saveFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BizException(BizErrorCode.FILE_EMPTY);
        }

        String originalName = file.getOriginalFilename();
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + extension;

        try {
            Path targetPath = uploadDir.resolve(filename);
            file.transferTo(targetPath);
            log.info("文件已保存: {} -> {}", originalName, targetPath);
            return "/uploads/" + filename;
        } catch (IOException e) {
            log.error("文件保存失败: {}", originalName, e);
            throw new BizException(BizErrorCode.FILE_SAVE_FAILED);
        }
    }
}
