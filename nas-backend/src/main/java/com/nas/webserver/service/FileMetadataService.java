package com.nas.webserver.service;

import com.nas.webserver.model.FileMetadata;
import com.nas.webserver.repository.FileMetadataRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Service
public class FileMetadataService {
    private final FileMetadataRepository repository;
    public FileMetadataService(FileMetadataRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void recordUpload(MultipartFile file, String folder) {
        String filename = file.getOriginalFilename();
        String path = (folder == null || folder.isBlank() ? "" : folder + "/") + filename;
        FileMetadata metadata = new FileMetadata();
        metadata.setStoragePath(path);
        metadata.setOriginalName(filename);
        metadata.setFileSize(file.getSize());
        metadata.setContentType(file.getContentType());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        metadata.setUploadedBy(auth == null ? null : auth.getName());
        repository.save(metadata);
    }

    @Transactional
    public void remove(String filename, String folder) {
        String path = (folder == null || folder.isBlank() ? "" : folder + "/") + filename;
        repository.deleteByStoragePath(path);
    }

    public List<FileMetadata> list() {
        return repository.findAllByOrderByUploadedAtDesc();
    }
}
