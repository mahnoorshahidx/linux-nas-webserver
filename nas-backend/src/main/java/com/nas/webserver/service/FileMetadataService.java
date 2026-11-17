package com.nas.webserver.service;

import com.nas.webserver.model.FileMetadata;
import com.nas.webserver.repository.FileMetadataRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class FileMetadataService {
    private final FileMetadataRepository repository;
    private final UserStorageService userStorage;
    private final com.nas.webserver.repository.UserRepository users;

    public FileMetadataService(FileMetadataRepository repository,
                               UserStorageService userStorage, com.nas.webserver.repository.UserRepository users) {
        this.repository = repository;
        this.userStorage = userStorage;
        this.users = users;
    }

    // Include the account directory so two users can upload the same filename.
    private String storageKey(String filename, String folder) throws IOException {
        String accountId = userStorage.root().getFileName().toString();
        String relativeName = (folder == null || folder.isBlank())
                ? filename : folder + "/" + filename;
        return ".accounts/" + accountId + "/" + relativeName;
    }

    @Transactional
    public void recordUpload(MultipartFile file, String folder) throws IOException {
        String filename = file.getOriginalFilename();
        FileMetadata metadata = new FileMetadata();
        metadata.setStoragePath(storageKey(filename, folder));
        metadata.setOriginalName(filename);
        metadata.setFileSize(file.getSize());
        metadata.setContentType(file.getContentType());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new org.springframework.security.access.AccessDeniedException("Login required");
        var owner = users.findByUsername(auth.getName()).orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Account not found"));
        metadata.setOwner(owner);
        metadata.setUploadedBy(owner.getUsername());
        repository.save(metadata);
    }

    @Transactional
    public void remove(String filename, String folder) throws IOException {
        repository.deleteByStoragePath(storageKey(filename, folder));
    }

    @Transactional
    public void renameFolder(String oldName, String newName) throws IOException {
        // Update metadata paths for every file inside the renamed folder.
        String oldPrefix = storageKey("", oldName);
        String newPrefix = storageKey("", newName);
        for (FileMetadata metadata : repository.findByStoragePathStartingWith(oldPrefix)) {
            metadata.setStoragePath(newPrefix + metadata.getStoragePath().substring(oldPrefix.length()));
        }
    }

    public List<FileMetadata> list() {
        // Only admins can reach this endpoint (enforced in SecurityConfig).
        return repository.findAllByOrderByUploadedAtDesc();
    }
}
