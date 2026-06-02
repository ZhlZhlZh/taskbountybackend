package com.firstteam.taskbountyplatform.file.service;

import com.firstteam.taskbountyplatform.common.exception.BusinessException;
import com.firstteam.taskbountyplatform.config.FileUploadConfig;
import com.firstteam.taskbountyplatform.config.PlatformConfig;
import com.firstteam.taskbountyplatform.file.entity.FileObject;
import com.firstteam.taskbountyplatform.file.repository.FileObjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * FileService - handles file upload, retrieval, deletion, and cleanup.
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private static final String DEFAULT_AVATAR_PATH = "/avatars/default.png";

    private final FileObjectRepository fileObjectRepository;
    private final FileUploadConfig fileUploadConfig;
    private final PlatformConfig platformConfig;

    public FileService(FileObjectRepository fileObjectRepository,
                        FileUploadConfig fileUploadConfig,
                        PlatformConfig platformConfig) {
        this.fileObjectRepository = fileObjectRepository;
        this.fileUploadConfig = fileUploadConfig;
        this.platformConfig = platformConfig;
    }

    /**
     * Upload a single file with business type validation.
     */
    @Transactional
    public FileObject uploadFile(MultipartFile file, String bizType, Long bizId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件不能为空");
        }

        // Validate file size based on bizType
        long maxSize = getMaxSizeForBizType(bizType);
        if (file.getSize() > maxSize) {
            throw new BusinessException(400, "文件大小超过限制: " + (maxSize / 1024 / 1024) + "MB");
        }

        // Validate file is not empty
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException(400, "文件名不能为空");
        }

        // Extract extension
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex);
        }

        // Generate unique stored name
        String storedName = UUID.randomUUID().toString() + extension;

        // Create directory structure
        String relativeDir = bizType + "/" + bizId;
        Path uploadDir = Paths.get(fileUploadConfig.getBaseDir(), relativeDir);
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            log.error("Failed to create directory: {}", uploadDir, e);
            throw new BusinessException(500, "文件上传失败：无法创建目录");
        }

        // Save file to disk
        Path targetPath = uploadDir.resolve(storedName);
        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("File saved to disk: {}", targetPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save file: {}", targetPath, e);
            throw new BusinessException(500, "文件上传失败：无法保存文件");
        }

        // Create FileObject entity
        FileObject fileObject = new FileObject();
        fileObject.setBizType(bizType);
        fileObject.setBizId(bizId);
        fileObject.setOriginalName(originalFilename);
        fileObject.setStoredName(storedName);
        fileObject.setFileUrl("/uploads/" + relativeDir + "/" + storedName);
        fileObject.setFileSize(file.getSize());
        fileObject.setContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");

        // Set expireAt for task attachments
        if (FileObject.BIZ_TYPE_TASK_ATTACHMENT.equals(bizType)
                || FileObject.BIZ_TYPE_DELIVERY_ATTACHMENT.equals(bizType)) {
            int saveDays = platformConfig.getAttachment().getSaveDays();
            fileObject.setExpireAt(LocalDateTime.now().plusDays(saveDays));
        }

        fileObject.setCreatedAt(LocalDateTime.now());
        FileObject saved = fileObjectRepository.save(fileObject);
        log.info("FileObject created: id={}, bizType={}, bizId={}, name={}",
                saved.getId(), bizType, bizId, originalFilename);
        return saved;
    }

    /**
     * Upload multiple files with total size and count validation.
     */
    @Transactional
    public List<FileObject> uploadFiles(List<MultipartFile> files, String bizType, Long bizId) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(400, "文件列表不能为空");
        }

        int maxFiles = fileUploadConfig.getMaxFilesPerTask();
        if (files.size() > maxFiles) {
            throw new BusinessException(400, "单次最多上传 " + maxFiles + " 个文件");
        }

        // Validate total size
        long maxSizePerFile = getMaxSizeForBizType(bizType);
        long totalSize = 0;
        for (MultipartFile file : files) {
            if (file.getSize() > maxSizePerFile) {
                throw new BusinessException(400,
                        "文件 " + file.getOriginalFilename() + " 大小超过限制: " + (maxSizePerFile / 1024 / 1024) + "MB");
            }
            totalSize += file.getSize();
        }

        List<FileObject> results = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            results.add(uploadFile(file, bizType, bizId));
        }
        return results;
    }

    /**
     * Get FileObject entity by ID.
     */
    @Transactional(readOnly = true)
    public FileObject getFile(Long fileId) {
        return fileObjectRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(404, "文件不存在"));
    }

    /**
     * Get all FileObjects for a business entity.
     */
    @Transactional(readOnly = true)
    public List<FileObject> getFilesByBizType(String bizType, Long bizId) {
        return fileObjectRepository.findByBizTypeAndBizId(bizType, bizId);
    }

    /**
     * Delete a file - removes physical file from disk and database record.
     */
    @Transactional
    public void deleteFile(Long fileId) {
        FileObject fileObject = fileObjectRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(404, "文件不存在"));

        // Delete physical file from disk
        Path filePath = resolvePhysicalPath(fileObject);
        try {
            Files.deleteIfExists(filePath);
            log.info("Physical file deleted: {}", filePath.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Failed to delete physical file: {}", filePath, e);
        }

        // Delete database record
        fileObjectRepository.delete(fileObject);
        log.info("FileObject deleted: id={}", fileId);
    }

    /**
     * Clean up expired files - called by scheduler.
     */
    @Transactional
    public void cleanExpiredFiles() {
        List<FileObject> expiredFiles = fileObjectRepository.findByExpireAtBefore(LocalDateTime.now());
        if (expiredFiles.isEmpty()) {
            log.debug("No expired files to clean");
            return;
        }

        int deletedCount = 0;
        for (FileObject fileObject : expiredFiles) {
            // Delete physical file
            Path filePath = resolvePhysicalPath(fileObject);
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.warn("Failed to delete expired file: {}", filePath, e);
            }
            // Delete record
            fileObjectRepository.delete(fileObject);
            deletedCount++;
        }
        log.info("Cleaned {} expired files", deletedCount);
    }

    /**
     * Validate avatar - checks dimensions, format, and size.
     */
    public void validateAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "头像文件不能为空");
        }

        // Check format
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BusinessException(400, "文件名不能为空");
        }
        String lower = originalFilename.toLowerCase();
        if (!lower.endsWith(".jpg") && !lower.endsWith(".jpeg") && !lower.endsWith(".png")) {
            throw new BusinessException(400, "头像仅支持 JPG/PNG 格式");
        }

        // Check size (< 1MB)
        if (file.getSize() > fileUploadConfig.getAvatarMaxSize()) {
            throw new BusinessException(400, "头像文件大小不能超过 1MB");
        }

        // Check dimensions (225x225)
        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new BusinessException(400, "无法读取图片，请确认文件为有效图片");
            }
            int width = image.getWidth();
            int height = image.getHeight();
            if (width != fileUploadConfig.getAvatarWidth() || height != fileUploadConfig.getAvatarHeight()) {
                throw new BusinessException(400,
                        "头像尺寸必须为 " + fileUploadConfig.getAvatarWidth() + "x"
                                + fileUploadConfig.getAvatarHeight() + "，当前为 " + width + "x" + height);
            }
        } catch (IOException e) {
            log.error("Failed to read image dimensions", e);
            throw new BusinessException(500, "头像验证失败");
        }
    }

    /**
     * Save a new avatar for a user, deleting the old one if not default.
     */
    @Transactional
    public String saveAvatar(MultipartFile file, Long userId) {
        validateAvatar(file);

        // Delete old avatar file if it exists and is not default
        List<FileObject> existingAvatars = fileObjectRepository
                .findByBizTypeAndBizId(FileObject.BIZ_TYPE_AVATAR, userId);
        for (FileObject oldAvatar : existingAvatars) {
            if (oldAvatar.getFileUrl() != null && !oldAvatar.getFileUrl().equals(DEFAULT_AVATAR_PATH)) {
                Path oldPath = resolvePhysicalPath(oldAvatar);
                try {
                    Files.deleteIfExists(oldPath);
                } catch (IOException e) {
                    log.warn("Failed to delete old avatar file: {}", oldPath, e);
                }
            }
            fileObjectRepository.delete(oldAvatar);
        }

        // Delete old avatars by bizType+bizId to be safe
        fileObjectRepository.deleteByBizTypeAndBizId(FileObject.BIZ_TYPE_AVATAR, userId);

        // Save new avatar
        FileObject newAvatar = uploadFile(file, FileObject.BIZ_TYPE_AVATAR, userId);
        log.info("Avatar saved for user {}: {}", userId, newAvatar.getFileUrl());
        return newAvatar.getFileUrl();
    }

    // ========== Private Helpers ==========

    /**
     * Get max file size based on business type.
     */
    private long getMaxSizeForBizType(String bizType) {
        return switch (bizType) {
            case FileObject.BIZ_TYPE_TASK_ATTACHMENT -> fileUploadConfig.getAttachmentMaxSize();
            case FileObject.BIZ_TYPE_DELIVERY_ATTACHMENT -> fileUploadConfig.getDeliveryMaxSize();
            case FileObject.BIZ_TYPE_AVATAR -> fileUploadConfig.getAvatarMaxSize();
            case FileObject.BIZ_TYPE_REPORT_EVIDENCE -> fileUploadConfig.getAttachmentMaxSize();
            default -> fileUploadConfig.getAttachmentMaxSize();
        };
    }

    /**
     * Resolve the physical file path from the FileObject's fileUrl.
     */
    private Path resolvePhysicalPath(FileObject fileObject) {
        String fileUrl = fileObject.getFileUrl();
        if (fileUrl == null || fileUrl.isBlank()) {
            return Paths.get(fileUploadConfig.getBaseDir(), "unknown");
        }
        // fileUrl format: /uploads/{bizType}/{bizId}/{storedName}
        // baseDir format: ./uploads
        // We need to strip the leading "/uploads/" and prepend baseDir
        String relativePath = fileUrl;
        if (relativePath.startsWith("/uploads/")) {
            relativePath = relativePath.substring("/uploads/".length());
        } else if (relativePath.startsWith("uploads/")) {
            relativePath = relativePath.substring("uploads/".length());
        }
        return Paths.get(fileUploadConfig.getBaseDir(), relativePath);
    }
}
