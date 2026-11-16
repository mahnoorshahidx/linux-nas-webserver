package com.nas.webserver.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.nas.webserver.model.FileMetadata;
import com.nas.webserver.model.User;
import com.nas.webserver.repository.FileMetadataRepository;
import com.nas.webserver.repository.UserRepository;

class BackupMetadataBridgeTest {
    @TempDir Path temp;

    @Test
    void completesMissingMetadataAndRestoresIt() throws Exception {
        FileMetadataRepository repository = mock(FileMetadataRepository.class);
        UserRepository users = mock(UserRepository.class);
        User user = new User();
        user.setUserId(2L);
        user.setUsername("nasadmin");
        when(users.findById(2L)).thenReturn(Optional.of(user));
        when(users.existsById(2L)).thenReturn(true);
        when(repository.findByStoragePath(any())).thenReturn(Optional.empty());
        List<FileMetadata> saved = new ArrayList<>();
        when(repository.save(any(FileMetadata.class))).thenAnswer(call -> {
            FileMetadata item = call.getArgument(0);
            saved.add(item);
            return item;
        });

        Path staged = temp.resolve("staged");
        Path file = staged.resolve(".accounts/2/test.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Testing backup restore");
        BackupMetadataBridge bridge = new BackupMetadataBridge(repository, users);

        // Simulates a ZIP manifest that omitted a file's metadata.
        var records = bridge.completeSnapshot(bridge.parse("NAS-METADATA-1\n"), staged);
        assertEquals(1, records.size());
        bridge.validate(records, staged, temp.resolve("uploads"));
        bridge.restore(records);
        assertEquals(1, saved.size());
        assertEquals("test.txt", saved.get(0).getOriginalName());
        assertEquals(".accounts/2/test.txt", saved.get(0).getStoragePath());
        assertEquals("nasadmin", saved.get(0).getUploadedBy());
    }

    @Test
    void preservesOriginalMetadataWhenPresent() throws Exception {
        FileMetadataRepository repository = mock(FileMetadataRepository.class);
        UserRepository users = mock(UserRepository.class);
        User user = new User();
        user.setUserId(2L);
        user.setUsername("nasadmin");
        when(users.findById(2L)).thenReturn(Optional.of(user));
        Path staged = temp.resolve("staged2");
        Path file = staged.resolve(".accounts/2/test.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Hello");
        BackupMetadataBridge bridge = new BackupMetadataBridge(repository, users);
        String manifest = "NAS-METADATA-1\n"
                + "LmFjY291bnRzLzIvdGVzdC50eHQ=\tdGVzdC50eHQ=\t5\t\t\t2026-11-01T12:00:00\n";
        var records = bridge.completeSnapshot(bridge.parse(manifest), staged);
        assertEquals(1, records.size());
        assertEquals(java.time.LocalDateTime.parse("2026-11-01T12:00:00"), records.get(0).uploadedAt());
    }
}
