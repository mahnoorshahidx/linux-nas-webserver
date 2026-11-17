package com.nas.webserver.repository;

import com.nas.webserver.model.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {
    List<FileMetadata> findByOwner_UserId(Long userId);
    Optional<FileMetadata> findByStoragePath(String storagePath);
    List<FileMetadata> findAllByOrderByUploadedAtDesc();
    void deleteByStoragePath(String storagePath);
 // Finds file metadata records inside a specific folder.
    List<FileMetadata> findByStoragePathStartingWith(String storagePath);
}
