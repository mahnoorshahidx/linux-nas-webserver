package com.nas.webserver.controller;

import com.nas.webserver.model.FileMetadata;
import com.nas.webserver.service.FilePermissionService;
import com.nas.webserver.service.UserStorageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/sharing")
public class FileSharingController {
    private final FilePermissionService sharing;
    private final UserStorageService storage;
    public FileSharingController(FilePermissionService sharing, UserStorageService storage) {
        this.sharing = sharing; this.storage = storage;
    }
    public record FileView(Long id, String name, String owner, long size, boolean mine) {
        public static FileView of(FileMetadata f, Long userId) {
            return new FileView(f.getId(), f.getOriginalName(), f.getOwner() == null ? f.getUploadedBy() : f.getOwner().getUsername(),
                f.getFileSize(), f.getOwner() != null && f.getOwner().getUserId().equals(userId));
        }
    }
    public record ShareRequest(@NotBlank String username, boolean canRead, boolean canWrite, boolean canEdit) {}
    @GetMapping("/files")
    public List<FileView> visible(Authentication auth) {
        var actor = sharing.currentUser(auth);
        return sharing.visible(actor).stream().map(f -> FileView.of(f, actor.getUserId())).toList();
    }
    @GetMapping("/files/{id}/access")
    public List<FilePermissionService.AccessEntry> access(@PathVariable Long id, Authentication auth) {
        return sharing.accessList(id, sharing.currentUser(auth));
    }
    @GetMapping("/files/{id}/lookup")
    public FilePermissionService.UserSummary lookup(@PathVariable Long id, @RequestParam String query, Authentication auth) {
        return sharing.lookupRecipient(id, sharing.currentUser(auth), query);
    }
    @PutMapping("/files/{id}/permissions")
    public ResponseEntity<Void> grant(@PathVariable Long id, @Valid @RequestBody ShareRequest req, Authentication auth) {
        sharing.grant(id, sharing.currentUser(auth), req.username(), req.canRead(), req.canWrite(), req.canEdit());
        return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/files/{id}/permissions/{userId}")
    public ResponseEntity<Void> revoke(@PathVariable Long id, @PathVariable Long userId, Authentication auth) {
        sharing.revoke(id, sharing.currentUser(auth), userId);
        return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/files/{id}")
    public ResponseEntity<Void> deleteOwnedFile(@PathVariable Long id, Authentication auth) throws IOException {
        sharing.deleteOwnedFile(id, sharing.currentUser(auth), storage);
        return ResponseEntity.noContent().build();
    }
    @ExceptionHandler(java.nio.file.NoSuchFileException.class)
    public ResponseEntity<String> missingFile(java.nio.file.NoSuchFileException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File missing");
    }
    @GetMapping("/files/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id, Authentication auth) throws IOException {
        FileMetadata file = sharing.requireReadable(id, sharing.currentUser(auth));
        Path path = storage.resolveStoredFile(file.getStoragePath());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(Files.size(path))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.getOriginalName()).build().toString())
            .body(new FileSystemResource(path));
    }
    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<String> notFound(java.util.NoSuchElementException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
}
