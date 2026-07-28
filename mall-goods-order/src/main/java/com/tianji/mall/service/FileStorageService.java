package com.tianji.mall.service;

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
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

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
     *
     * @param file 上传的文件
     * @return 访问路径，如 /uploads/a1b2c3d4.jpg
     */
    public String saveFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BizException(BizErrorCode.FILE_EMPTY);
        }

        // 校验文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(BizErrorCode.FILE_TOO_LARGE);
        }

        // 生成唯一文件名 + 校验类型
        String originalName = file.getOriginalFilename();
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException(BizErrorCode.FILE_TYPE_UNSUPPORTED);
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
