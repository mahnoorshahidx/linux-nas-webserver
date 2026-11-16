package com.nas.webserver.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class BackupRestoreSafetyTest {
    @TempDir Path temp;

    private BackupService service() throws IOException {
        return new BackupService(temp.resolve("uploads").toString(), temp.resolve("backups").toString());
    }

    @Test void restoresMissingFileWithoutRemovingOtherData() throws IOException {
        BackupService backup = service();
        Path storage = temp.resolve("uploads");
        Path account = Files.createDirectories(storage.resolve(".accounts/2"));
        Files.writeString(account.resolve("test.txt"), "backup data");
        String zip = backup.createBackup();
        Files.delete(account.resolve("test.txt"));
        Files.writeString(storage.resolve("unrelated.txt"), "keep me");
        backup.restoreBackup(zip);
        assertEquals("backup data", Files.readString(account.resolve("test.txt")));
        assertEquals("keep me", Files.readString(storage.resolve("unrelated.txt")));
        backup.restoreBackup(zip); // Identical existing files are allowed.
    }

    @Test void conflictDoesNotRestoreOtherFiles() throws IOException {
        BackupService backup = service();
        Path storage = temp.resolve("uploads");
        Files.writeString(storage.resolve("a.txt"), "old");
        Files.writeString(storage.resolve("b.txt"), "backup");
        String zip = backup.createBackup();
        Files.writeString(storage.resolve("a.txt"), "changed");
        Files.delete(storage.resolve("b.txt"));
        assertThrows(FileAlreadyExistsException.class, () -> backup.restoreBackup(zip));
        assertFalse(Files.exists(storage.resolve("b.txt")));
        assertEquals("changed", Files.readString(storage.resolve("a.txt")));
    }

    @Test void rejectsTraversalWithoutWritingOutsideStorage() throws IOException {
        BackupService backup = service();
        String name = "backup-00000000-0000-0000-0000-000000000000.zip";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp.resolve("backups").resolve(name)))) {
            zip.putNextEntry(new ZipEntry("../escape.txt"));
            zip.write("no".getBytes());
            zip.closeEntry();
        }
        assertThrows(IOException.class, () -> backup.restoreBackup(name));
        assertFalse(Files.exists(temp.resolve("escape.txt")));
    }
}
