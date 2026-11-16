package com.nas.webserver.repository;

import com.nas.webserver.model.FilePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FilePermissionRepository extends JpaRepository<FilePermission, Long> {
    Optional<FilePermission> findByFile_IdAndUser_UserId(Long fileId, Long userId);
    List<FilePermission> findByUser_UserId(Long userId);
    List<FilePermission> findByFile_Id(Long fileId);
}
