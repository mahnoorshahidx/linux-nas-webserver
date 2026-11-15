package com.nas.webserver.service;

import com.nas.webserver.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;

@Service
public class UserStorageService {
    private final Path storagePath;
    private final UserRepository users;

    public UserStorageService(
            @Value("${nas.storage.path}") String storage,
            UserRepository users) throws IOException {
        this.storagePath = Path.of(storage).toAbsolutePath().normalize();
        this.users = users;
        Files.createDirectories(storagePath);
        if (!Files.isDirectory(storagePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Storage must be a real directory");
        }
    }

    public Path root() throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Sign in to access storage");
        }

        Long id = users.findByUsername(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Account not found"))
                .getUserId();

        if (id == null || id <= 0) {
            throw new AccessDeniedException("Invalid account");
        }

        Path accounts = storagePath.resolve(".accounts");
        ensureDirectory(accounts);
        Path account = accounts.resolve(Long.toString(id));
        ensureDirectory(account);
        return account;
    }

    private void ensureDirectory(Path directory) throws IOException {
        try {
            Files.createDirectory(directory);
        } catch (FileAlreadyExistsException e) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Unsafe storage directory");
            }
        }
    }
}
