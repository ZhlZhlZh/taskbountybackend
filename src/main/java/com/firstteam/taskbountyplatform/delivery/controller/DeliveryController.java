package com.firstteam.taskbountyplatform.delivery.controller;

import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.response.ApiResponse;
import com.firstteam.taskbountyplatform.delivery.entity.Delivery;
import com.firstteam.taskbountyplatform.delivery.service.DeliveryService;
import com.firstteam.taskbountyplatform.file.entity.FileObject;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks")
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final UserContext userContext;

    public DeliveryController(DeliveryService deliveryService, UserContext userContext) {
        this.deliveryService = deliveryService;
        this.userContext = userContext;
    }

    /**
     * Submit a delivery for a task. Only the worker (winner) can submit.
     */
    @PostMapping("/{taskId}/deliveries")
    public ApiResponse<Map<String, Object>> submitDelivery(
            @PathVariable Long taskId,
            @RequestParam(value = "description", required = false, defaultValue = "") String description,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        Long currentUserId = userContext.getCurrentUserId();
        Delivery delivery = deliveryService.submitDelivery(taskId, currentUserId, description, files);

        // Get files for the response
        List<FileObject> deliveryFiles = deliveryService.getDeliveryFiles(delivery.getId());
        List<Map<String, Object>> fileList = deliveryFiles.stream().map(f -> {
            Map<String, Object> fileMap = new HashMap<>();
            fileMap.put("id", f.getId());
            fileMap.put("originalName", f.getOriginalName());
            fileMap.put("fileUrl", f.getFileUrl());
            fileMap.put("fileSize", f.getFileSize());
            fileMap.put("contentType", f.getContentType());
            return fileMap;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("delivery", delivery);
        result.put("files", fileList);

        return ApiResponse.success("交付物提交成功", result);
    }

    /**
     * Publisher confirms a delivery is complete and accepted.
     */
    @PostMapping("/{taskId}/confirm")
    public ApiResponse<Void> confirmComplete(@PathVariable Long taskId) {
        Long currentUserId = userContext.getCurrentUserId();
        deliveryService.confirmComplete(taskId, currentUserId, "MANUAL");
        return ApiResponse.success("任务已确认完成，点券已转账", null);
    }

    /**
     * Publisher rejects a delivery, requesting revision.
     */
    @PostMapping("/{taskId}/deliveries/{deliveryId}/reject")
    public ApiResponse<Void> rejectDelivery(
            @PathVariable Long taskId,
            @PathVariable Long deliveryId) {
        Long currentUserId = userContext.getCurrentUserId();
        deliveryService.rejectDelivery(taskId, currentUserId, deliveryId);
        return ApiResponse.success("交付物已退回，接单者可重新提交", null);
    }

    /**
     * Get a specific delivery for a task.
     */
    @GetMapping("/{taskId}/deliveries/{deliveryId}")
    public ApiResponse<Map<String, Object>> getDeliveryForTask(
            @PathVariable Long taskId,
            @PathVariable Long deliveryId) {
        Long currentUserId = userContext.getCurrentUserId();
        Delivery delivery = deliveryService.getDeliveryForTask(taskId, currentUserId);

        // Load associated files
        List<FileObject> deliveryFiles = deliveryService.getDeliveryFiles(delivery.getId());
        List<Map<String, Object>> fileList = deliveryFiles.stream().map(f -> {
            Map<String, Object> fileMap = new HashMap<>();
            fileMap.put("id", f.getId());
            fileMap.put("originalName", f.getOriginalName());
            fileMap.put("fileUrl", f.getFileUrl());
            fileMap.put("fileSize", f.getFileSize());
            fileMap.put("contentType", f.getContentType());
            return fileMap;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("delivery", delivery);
        result.put("files", fileList);

        return ApiResponse.success(result);
    }

    /**
     * Get all deliveries (submission history) for a task.
     */
    @GetMapping("/{taskId}/deliveries")
    public ApiResponse<List<Map<String, Object>>> getDeliveriesForTask(@PathVariable Long taskId) {
        Long currentUserId = userContext.getCurrentUserId();
        List<Delivery> deliveries = deliveryService.getDeliveriesForTask(taskId, currentUserId);

        List<Map<String, Object>> result = deliveries.stream().map(delivery -> {
            Map<String, Object> map = new HashMap<>();
            map.put("delivery", delivery);

            List<FileObject> deliveryFiles = deliveryService.getDeliveryFiles(delivery.getId());
            List<Map<String, Object>> fileList = deliveryFiles.stream().map(f -> {
                Map<String, Object> fileMap = new HashMap<>();
                fileMap.put("id", f.getId());
                fileMap.put("originalName", f.getOriginalName());
                fileMap.put("fileUrl", f.getFileUrl());
                fileMap.put("fileSize", f.getFileSize());
                fileMap.put("contentType", f.getContentType());
                return fileMap;
            }).collect(Collectors.toList());
            map.put("files", fileList);
            return map;
        }).collect(Collectors.toList());

        return ApiResponse.success(result);
    }
}
