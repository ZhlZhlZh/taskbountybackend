package com.firstteam.taskbountyplatform.scheduler;

import com.firstteam.taskbountyplatform.config.FileUploadConfig;
import com.firstteam.taskbountyplatform.file.entity.FileObject;
import com.firstteam.taskbountyplatform.file.repository.FileObjectRepository;
import com.firstteam.taskbountyplatform.task.entity.MessageBoard;
import com.firstteam.taskbountyplatform.task.entity.Task;
import com.firstteam.taskbountyplatform.common.enums.TaskStatus;
import com.firstteam.taskbountyplatform.task.repository.MessageBoardRepository;
import com.firstteam.taskbountyplatform.task.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Scheduler that handles file cleanup operations:
 * deleting expired files from disk and database, and cleaning old chat records.
 */
@Component
public class FileCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupScheduler.class);

    private final FileObjectRepository fileObjectRepository;
    private final FileUploadConfig fileUploadConfig;
    private final MessageBoardRepository messageBoardRepository;
    private final TaskRepository taskRepository;

    public FileCleanupScheduler(FileObjectRepository fileObjectRepository,
                                FileUploadConfig fileUploadConfig,
                                MessageBoardRepository messageBoardRepository,
                                TaskRepository taskRepository) {
        this.fileObjectRepository = fileObjectRepository;
        this.fileUploadConfig = fileUploadConfig;
        this.messageBoardRepository = messageBoardRepository;
        this.taskRepository = taskRepository;
    }

    /**
     * Daily at 4:00 AM: delete expired files from disk and from the database.
     */
    @Scheduled(cron = "0 0 4 * * ?")
    @Transactional
    public void cleanExpiredFiles() {
        log.info("Starting cleanExpiredFiles...");
        LocalDateTime now = LocalDateTime.now();

        List<FileObject> expiredFiles = fileObjectRepository.findByExpireAtBefore(now);
        log.info("Found {} expired file records", expiredFiles.size());

        int deletedPhysicalCount = 0;
        int deletedDbCount = 0;
        List<String> failedDeletions = new ArrayList<>();

        for (FileObject file : expiredFiles) {
            try {
                // Delete physical file from disk
                boolean physicalDeleted = deletePhysicalFile(file);
                if (physicalDeleted) {
                    deletedPhysicalCount++;
                } else {
                    log.warn("Physical file not found for FileObject id={}, storedName={}",
                            file.getId(), file.getStoredName());
                }

                // Delete database record
                fileObjectRepository.delete(file);
                deletedDbCount++;

            } catch (Exception e) {
                String msg = "Failed to clean FileObject id=" + file.getId()
                        + " storedName=" + file.getStoredName() + ": " + e.getMessage();
                log.error(msg, e);
                failedDeletions.add(msg);
            }
        }

        log.info("cleanExpiredFiles completed. Physical files deleted: {}, DB records deleted: {}, Failures: {}",
                deletedPhysicalCount, deletedDbCount, failedDeletions.size());

        if (!failedDeletions.isEmpty()) {
            log.warn("Failed to clean some files: {}", String.join("; ", failedDeletions));
        }
    }

    /**
     * Delete the physical file from disk. Returns true if file existed and was deleted.
     */
    protected boolean deletePhysicalFile(FileObject fileObject) {
        if (fileObject.getFileUrl() == null || fileObject.getFileUrl().isBlank()) {
            log.debug("FileObject id={} has no fileUrl, skipping physical deletion", fileObject.getId());
            return false;
        }

        try {
            // Determine the actual file path on disk
            // fileUrl typically contains a relative URL like "/files/xxx"
            // We need to resolve it against the base upload directory
            String baseDir = fileUploadConfig.getBaseDir();
            String fileUrl = fileObject.getFileUrl();

            Path filePath;
            if (fileUrl.startsWith("/files/")) {
                // Extract the relative path after /files/ and resolve against baseDir
                String relativePath = fileUrl.substring("/files/".length());
                filePath = Paths.get(baseDir, relativePath.trim());
            } else if (fileUrl.startsWith("files/")) {
                String relativePath = fileUrl.substring("files/".length());
                filePath = Paths.get(baseDir, relativePath.trim());
            } else {
                // Try to resolve the fileUrl directly as a path
                filePath = Paths.get(fileUrl.trim());
            }

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.debug("Deleted physical file: {}", filePath);
                return true;
            } else {
                log.debug("Physical file not found at path: {}", filePath);
                return false;
            }
        } catch (IOException e) {
            log.error("Failed to delete physical file for FileObject id={}: {}",
                    fileObject.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Daily at 4:00 AM: clean message_board records for tasks that have been
     * COMPLETED or CANCELLED for more than 30 days.
     */
    @Scheduled(cron = "0 0 4 * * ?")
    @Transactional
    public void cleanExpiredChatRecords() {
        log.info("Starting cleanExpiredChatRecords...");
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);

        // Find all tasks that are COMPLETED or CANCELLED and finished more than 30 days ago
        List<Task> allTasks = taskRepository.findAll();
        List<Task> expiredTaskMessages = allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED || t.getStatus() == TaskStatus.CANCELLED)
                .filter(t -> {
                    LocalDateTime endTime = t.getCompletedAt() != null
                            ? t.getCompletedAt()
                            : t.getCancelledAt();
                    return endTime != null && endTime.isBefore(cutoff);
                })
                .toList();

        int totalDeleted = 0;
        for (Task task : expiredTaskMessages) {
            try {
                // Find and count messages for this task before deletion
                List<MessageBoard> messages = messageBoardRepository.findByTaskIdOrderBySentAtAsc(task.getId());
                if (!messages.isEmpty()) {
                    // Delete messages older than the cutoff
                    messageBoardRepository.deleteByTaskIdAndSentAtBefore(task.getId(), cutoff);
                    totalDeleted += messages.size();
                    log.debug("Cleaned {} messages for task id={} (status={})",
                            messages.size(), task.getId(), task.getStatus());
                }
            } catch (Exception e) {
                log.error("Failed to clean chat records for task id={}: {}", task.getId(), e.getMessage(), e);
            }
        }

        log.info("cleanExpiredChatRecords completed. Deleted {} messages across {} completed/cancelled tasks",
                totalDeleted, expiredTaskMessages.size());
    }
}
