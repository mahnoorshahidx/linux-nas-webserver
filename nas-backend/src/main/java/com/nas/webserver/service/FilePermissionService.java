package com.nas.webserver.service;

import com.nas.webserver.model.*;
import com.nas.webserver.repository.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class FilePermissionService {
    private final FileMetadataRepository files;
    private final FilePermissionRepository permissions;
    private final UserRepository users;
    public FilePermissionService(FileMetadataRepository files, FilePermissionRepository permissions, UserRepository users) {
        this.files = files; this.permissions = permissions; this.users = users;
    }
    public User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) throw new AccessDeniedException("Login required");
        return users.findByUsername(authentication.getName()).orElseThrow(() -> new AccessDeniedException("Account missing"));
    }
    private boolean admin(User user) { return user.getRole() != null && "ADMIN".equalsIgnoreCase(user.getRole().getRoleName()); }
    private boolean owner(FileMetadata file, User user) { return file.getOwner() != null && Objects.equals(file.getOwner().getUserId(), user.getUserId()); }
    private FileMetadata file(Long id) { return files.findById(id).orElseThrow(() -> new NoSuchElementException("File not found")); }
    private void requireOwner(FileMetadata file, User actor) {
        if (!owner(file, actor) && !admin(actor)) throw new AccessDeniedException("Only the owner or administrator may share this file");
    }
    @Transactional(readOnly = true)
    public boolean canRead(FileMetadata file, User actor) {
        if (owner(file, actor) || admin(actor)) return true;
        return permissions.findByFile_IdAndUser_UserId(file.getId(), actor.getUserId())
            .map(p -> p.isCanRead() || p.isCanWrite() || p.isCanEdit()).orElse(false);
    }
    @Transactional(readOnly = true)
    public boolean canWrite(FileMetadata file, User actor) {
        if (owner(file, actor) || admin(actor)) return true;
        return permissions.findByFile_IdAndUser_UserId(file.getId(), actor.getUserId()).map(FilePermission::isCanWrite).orElse(false);
    }
    @Transactional(readOnly = true)
    public boolean canEdit(FileMetadata file, User actor) {
        if (owner(file, actor) || admin(actor)) return true;
        return permissions.findByFile_IdAndUser_UserId(file.getId(), actor.getUserId()).map(FilePermission::isCanEdit).orElse(false);
    }
    @Transactional(readOnly = true)
    public FileMetadata requireReadable(Long fileId, User actor) {
        FileMetadata file = file(fileId);
        if (!canRead(file, actor)) throw new AccessDeniedException("File is private");
        return file;
    }
    public record UserSummary(Long id, String username, String email) {
        public static UserSummary of(User user) { return new UserSummary(user.getUserId(), user.getUsername(), user.getEmail()); }
    }
    public record AccessEntry(Long userId, String username, String email, boolean owner,
                              boolean canRead, boolean canWrite, boolean canEdit) {}
    @Transactional(readOnly = true)
    public UserSummary lookupRecipient(Long fileId, User actor, String query) {
        requireOwner(file(fileId), actor);
        String value = query == null ? "" : query.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Enter a username or email");
        User recipient = users.findByUsername(value)
            .or(() -> users.findByEmail(value))
            .orElseThrow(() -> new NoSuchElementException("User not found"));
        return UserSummary.of(recipient);
    }
    @Transactional(readOnly = true)
    public List<AccessEntry> accessList(Long fileId, User actor) {
        FileMetadata file = file(fileId);
        requireOwner(file, actor);
        List<AccessEntry> result = new ArrayList<>();
        if (file.getOwner() != null) {
            User user = file.getOwner();
            result.add(new AccessEntry(user.getUserId(), user.getUsername(), user.getEmail(), true, true, true, true));
        }
        for (FilePermission permission : permissions.findByFile_Id(fileId)) {
            User user = permission.getUser();
            if (file.getOwner() != null && Objects.equals(user.getUserId(), file.getOwner().getUserId())) continue;
            result.add(new AccessEntry(user.getUserId(), user.getUsername(), user.getEmail(), false,
                permission.isCanRead(), permission.isCanWrite(), permission.isCanEdit()));
        }
        return result;
    }
    @Transactional
    public void grant(Long fileId, User actor, String recipientUsername, boolean read, boolean write, boolean edit) {
        FileMetadata file = file(fileId);
        requireOwner(file, actor);
        User recipient = users.findByUsername(recipientUsername)
            .or(() -> users.findByEmail(recipientUsername))
            .orElseThrow(() -> new NoSuchElementException("User not found"));
        if (owner(file, recipient)) throw new IllegalArgumentException("Owner already has full access");
        if (!read && !write && !edit) {
            permissions.findByFile_IdAndUser_UserId(fileId, recipient.getUserId()).ifPresent(permissions::delete);
            return;
        }
        FilePermission p = permissions.findByFile_IdAndUser_UserId(fileId, recipient.getUserId()).orElseGet(FilePermission::new);
        p.setFile(file); p.setUser(recipient); p.setCanRead(read); p.setCanWrite(write); p.setCanEdit(edit);
        permissions.save(p);
    }
    @Transactional
    public void revoke(Long fileId, User actor, Long recipientId) {
        requireOwner(file(fileId), actor);
        permissions.findByFile_IdAndUser_UserId(fileId, recipientId).ifPresent(permissions::delete);
    }
    @Transactional
    public void deleteOwnedFile(Long fileId, User actor, UserStorageService storage) throws java.io.IOException {
        FileMetadata target = file(fileId);
        requireOwner(target, actor);
        // Resolve only account-scoped keys. An old metadata row may point to a
        // file in a previous project directory; allow its stale row to be removed.
        try {
            java.nio.file.Path path = storage.resolveStoredFile(target.getStoragePath());
            java.nio.file.Files.delete(path);
        } catch (java.nio.file.NoSuchFileException missing) {
            // Validated key, but no physical file under this build's storage root.
        }
        permissions.deleteAll(permissions.findByFile_Id(fileId));
        files.delete(target);
    }

    @Transactional(readOnly = true)
    public List<FileMetadata> visible(User actor) {
        if (admin(actor)) return files.findAll();
        Map<Long, FileMetadata> result = new LinkedHashMap<>();
        for (FileMetadata file : files.findByOwner_UserId(actor.getUserId())) result.put(file.getId(), file);
        for (FilePermission permission : permissions.findByUser_UserId(actor.getUserId())) {
            if (permission.isCanRead() || permission.isCanWrite() || permission.isCanEdit())
                result.put(permission.getFile().getId(), permission.getFile());
        }
        return new ArrayList<>(result.values());
    }
}
