package com.nas.webserver.controller;

import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

@RestController
public class StorageController {
    private final com.nas.webserver.service.UserStorageService userStorage;
    public StorageController(
            com.nas.webserver.service.UserStorageService userStorage) {
        this.userStorage = userStorage;
    }
    @GetMapping("/api/storage/overview")
    public Map<String,Object> overview() throws IOException {
        Path root = userStorage.root();
        long[] bytes = {0}, files = {0}, folders = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root)) folders[0]++;
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) { files[0]++; bytes[0] += attrs.size(); }
                return FileVisitResult.CONTINUE;
            }
        });
        FileStore fs = Files.getFileStore(root);
        return Map.of("fileCount",files[0],"folderCount",folders[0],
            "usedBytes",bytes[0],"totalBytes",fs.getTotalSpace(),
            "usableBytes",fs.getUsableSpace());
    }
}
