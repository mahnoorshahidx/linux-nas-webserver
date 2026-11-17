package com.nas.webserver.service;

import com.nas.webserver.model.*;
import com.nas.webserver.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BackupShareRestoreTest {
    @TempDir Path temp;

    private class Fixture {
        final FileMetadataRepository files = mock(FileMetadataRepository.class);
        final FilePermissionRepository permissions = mock(FilePermissionRepository.class);
        final UserRepository users = mock(UserRepository.class);
        final Map<String, FileMetadata> rows = new HashMap<>();
        final List<FilePermission> shares = new ArrayList<>();
        final User owner = new User();
        final User recipient = new User();
        final Path storage;
        final Path file;
        final String key = ".accounts/1/share-test.txt";
        final BackupService backups;

        Fixture() throws Exception {
            storage = temp.resolve("uploads");
            file = storage.resolve(key);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "Disposable sharing restore test");
            owner.setUserId(1L);
            owner.setUsername("owner");
            recipient.setUserId(2L);
            recipient.setUsername("recipient");
            when(users.findById(1L)).thenReturn(Optional.of(owner));
            when(users.existsById(1L)).thenReturn(true);
            when(users.findByUsername("recipient")).thenReturn(Optional.of(recipient));

            when(files.findAll()).thenAnswer(c -> new ArrayList<>(rows.values()));
            when(files.findByStoragePath(anyString())).thenAnswer(c ->
                Optional.ofNullable(rows.get(c.getArgument(0))));
            when(files.findById(anyLong())).thenAnswer(c ->
                rows.values().stream().findFirst());
            when(files.save(any(FileMetadata.class))).thenAnswer(c -> {
                FileMetadata saved = spy((FileMetadata)c.getArgument(0));
                doReturn(10L).when(saved).getId();
                rows.put(saved.getStoragePath(), saved);
                return saved;
            });
            doAnswer(c -> {
                rows.remove(((FileMetadata)c.getArgument(0)).getStoragePath());
                return null;
            }).when(files).delete(any(FileMetadata.class));

            when(permissions.findByFile_Id(anyLong())).thenAnswer(c ->
                new ArrayList<>(shares));
            when(permissions.findByFile_IdAndUser_UserId(anyLong(), anyLong()))
                .thenAnswer(c -> shares.stream().filter(p ->
                    p.getUser().getUserId().equals(c.getArgument(1))).findFirst());
            when(permissions.save(any(FilePermission.class))).thenAnswer(c -> {
                FilePermission p = c.getArgument(0);
                shares.add(p);
                return p;
            });
            doAnswer(c -> {
                Iterable<?> deleted = c.getArgument(0);
                for (Object p : deleted) shares.remove(p);
                return null;
            }).when(permissions).deleteAll(anyIterable());

            FileMetadata row = new FileMetadata();
            row.setStoragePath(key);
            row.setOriginalName("share-test.txt");
            row.setFileSize(Files.size(file));
            row.setContentType("text/plain");
            row.setUploadedBy("owner");
            row.setOwner(owner);
            files.save(row);

            backups = new BackupService(storage.toString(),
                temp.resolve("backups").toString());
            backups.setMetadata(new BackupMetadataBridge(files, users, permissions));
        }

        FilePermissionService sharing() {
            return new FilePermissionService(files, permissions, users);
        }

        void share(boolean write, boolean edit) {
            sharing().grant(10L, owner, "recipient", true, write, edit);
        }

        void delete() throws Exception {
            UserStorageService resolver = mock(UserStorageService.class);
            when(resolver.resolveStoredFile(key)).thenReturn(file);
            sharing().deleteOwnedFile(10L, owner, resolver);
            assertFalse(Files.exists(file));
            assertTrue(rows.isEmpty());
            assertTrue(shares.isEmpty());
        }
    }

    @Test
    void restoresReadOnlyShareAfterActualFileDeletion() throws Exception {
        Fixture f = new Fixture();
        f.share(false, false);
        String archive = f.backups.createBackup();
        f.delete();
        Files.writeString(f.storage.resolve("unrelated.txt"), "Keep me");
        f.backups.restoreBackup(archive);
        assertEquals("Disposable sharing restore test", Files.readString(f.file));
        assertEquals("Keep me", Files.readString(f.storage.resolve("unrelated.txt")));
        assertEquals(1, f.shares.size());
        FilePermission p = f.shares.getFirst();
        assertEquals("recipient", p.getUser().getUsername());
        assertTrue(p.isCanRead());
        assertFalse(p.isCanWrite());
        assertFalse(p.isCanEdit());
    }

    @Test
    void restoresWriteAndEditShare() throws Exception {
        Fixture f = new Fixture();
        f.share(true, true);
        String archive = f.backups.createBackup();
        f.delete();
        f.backups.restoreBackup(archive);
        assertEquals(1, f.shares.size());
        assertTrue(f.shares.getFirst().isCanRead());
        assertTrue(f.shares.getFirst().isCanWrite());
        assertTrue(f.shares.getFirst().isCanEdit());
    }

    @Test
    void identicalExistingFileKeepsCurrentSharesWithoutDuplicates() throws Exception {
        Fixture f = new Fixture();
        f.share(false, false);
        String archive = f.backups.createBackup();
        FilePermission current = f.shares.getFirst();
        current.setCanWrite(true);
        current.setCanEdit(true);
        f.backups.restoreBackup(archive);
        f.backups.restoreBackup(archive);
        assertEquals(1, f.shares.size());
        assertSame(current, f.shares.getFirst());
        assertTrue(current.isCanWrite());
        assertTrue(current.isCanEdit());
    }

    @Test
    void skipsMissingRecipientAndReportsIt() throws Exception {
        Fixture f = new Fixture();
        f.share(false, false);
        String archive = f.backups.createBackup();
        f.delete();
        when(f.users.findByUsername("recipient")).thenReturn(Optional.empty());
        String result = f.backups.restoreBackup(archive);
        assertTrue(Files.exists(f.file));
        assertTrue(f.shares.isEmpty());
        assertTrue(result.contains("Skipped share for missing user: recipient"));
    }
}
