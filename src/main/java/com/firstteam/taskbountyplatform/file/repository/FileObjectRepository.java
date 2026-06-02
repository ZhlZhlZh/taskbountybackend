package com.firstteam.taskbountyplatform.file.repository;

import com.firstteam.taskbountyplatform.file.entity.FileObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FileObjectRepository extends JpaRepository<FileObject, Long> {
    List<FileObject> findByBizTypeAndBizId(String bizType, Long bizId);
    List<FileObject> findByExpireAtBefore(LocalDateTime now);
    void deleteByBizTypeAndBizId(String bizType, Long bizId);
}
