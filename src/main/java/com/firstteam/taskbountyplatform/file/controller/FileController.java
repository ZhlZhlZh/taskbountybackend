package com.firstteam.taskbountyplatform.file.controller;

import com.firstteam.taskbountyplatform.common.response.ApiResponse;
import com.firstteam.taskbountyplatform.file.entity.FileObject;
import com.firstteam.taskbountyplatform.file.service.FileService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * FileController - REST API for file upload, download, and management.
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * POST /api/files/upload - upload a single file.
     */
    @PostMapping("/upload")
    public ApiResponse<FileObject> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bizType") String bizType,
            @RequestParam("bizId") Long bizId) {
        FileObject fileObject = fileService.uploadFile(file, bizType, bizId);
        return ApiResponse.success(fileObject);
    }

    /**
     * GET /api/files/{fileId} - get file info.
     */
    @GetMapping("/{fileId}")
    public ApiResponse<FileObject> getFile(@PathVariable Long fileId) {
        FileObject fileObject = fileService.getFile(fileId);
        return ApiResponse.success(fileObject);
    }

    /**
     * GET /api/files/{fileId}/download - download the physical file.
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long fileId) {
        FileObject fileObject = fileService.getFile(fileId);

        Path filePath = resolvePhysicalPath(fileObject);
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            InputStreamResource resource = new InputStreamResource(
                    new FileInputStream(filePath.toFile()));
            String contentType = fileObject.getContentType();
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + fileObject.getOriginalName() + "\"")
                    .body(resource);
        } catch (FileNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * DELETE /api/files/{fileId} - delete a file.
     */
    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> deleteFile(@PathVariable Long fileId) {
        fileService.deleteFile(fileId);
        return ApiResponse.success("文件已删除", null);
    }

    /**
     * GET /api/files/biz/{bizType}/{bizId} - get files by business type and ID.
     */
    @GetMapping("/biz/{bizType}/{bizId}")
    public ApiResponse<List<FileObject>> getFilesByBizType(
            @PathVariable String bizType,
            @PathVariable Long bizId) {
        List<FileObject> files = fileService.getFilesByBizType(bizType, bizId);
        return ApiResponse.success(files);
    }

    /**
     * Resolve the physical file path from a FileObject's fileUrl.
     */
    private Path resolvePhysicalPath(FileObject fileObject) {
        String fileUrl = fileObject.getFileUrl();
        if (fileUrl == null || fileUrl.isBlank()) {
            return Paths.get("./uploads", "unknown");
        }
        String relativePath = fileUrl;
        if (relativePath.startsWith("/uploads/")) {
            relativePath = relativePath.substring("/uploads/".length());
        } else if (relativePath.startsWith("uploads/")) {
            relativePath = relativePath.substring("uploads/".length());
        }
        return Paths.get("./uploads", relativePath);
    }
}
