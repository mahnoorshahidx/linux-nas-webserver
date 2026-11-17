package com.nas.webserver.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

@Service
public class FolderService {

    private final UserStorageService userStorage;

    public FolderService(UserStorageService userStorage) {
        this.userStorage = userStorage;
    }

    // Validate folder names and keep folders inside storage
    private Path resolveFolder(String name) throws IOException {
        if (name == null || name.isBlank()
                || name.equals(".") || name.equals("..")
                || name.contains("/") || name.contains("\\")
                || name.contains(":")) {
            throw new IllegalArgumentException("Invalid folder name");
        }

        Path folder = userStorage.root().resolve(name).normalize();

        if (!folder.getParent().equals(userStorage.root())) {
            throw new IllegalArgumentException("Invalid folder path");
        }

        return folder;
    }

    // Create a folder without overwriting
    public void createFolder(String name) throws IOException {
        Path folder = resolveFolder(name);
        Files.createDirectory(folder);
    }

    // List stored folders
    public List<String> listFolders() throws IOException {
        try (Stream<Path> folders = Files.list(userStorage.root())) {
            return folders
                    .filter(path -> Files.isDirectory(
                            path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.getFileName().toString())
                    .toList();
        }
    }

    // Rename an existing folder
    public void renameFolder(String oldName, String newName)
            throws IOException {
        Path oldFolder = resolveFolder(oldName);
        Path newFolder = resolveFolder(newName);

        if (!Files.isDirectory(oldFolder, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException(oldName);
        }

        if (Files.exists(newFolder, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(newName);
        }

        Files.move(oldFolder, newFolder);
    }

    // Delete an empty folder
    public void deleteFolder(String name) throws IOException {
        Path folder = resolveFolder(name);

        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException(name);
        }

        Files.delete(folder);
    }
}
