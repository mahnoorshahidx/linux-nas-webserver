package com.nas.webserver.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.CRC32;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BackupService {
    private static final long MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024;
    private static final long MAX_ENTRY_BYTES = 512L * 1024 * 1024;
    private static final int MAX_ENTRIES = 10000;
    private static final long MAX_MANIFEST_BYTES = 8L * 1024 * 1024;
    private final Path storagePath;
    private final Path backupPath;
    private BackupMetadataBridge metadata;

    public BackupService(@Value("${nas.storage.path}") String storage,
                         @Value("${nas.backup.path}") String backups) throws IOException {
        storagePath = Paths.get(storage).toAbsolutePath().normalize();
        backupPath = Paths.get(backups).toAbsolutePath().normalize();
        if (backupPath.startsWith(storagePath) || storagePath.startsWith(backupPath)) {
            throw new IllegalArgumentException("Storage and backup directories must be separate");
        }
        Files.createDirectories(storagePath);
        Files.createDirectories(backupPath);
        if (!Files.isDirectory(storagePath, LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(backupPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Storage and backup paths must be real directories");
        }
    }

    // Optional setter retains compatibility with existing standalone service tests.
    @Autowired(required = false)
    public void setMetadata(BackupMetadataBridge metadata) { this.metadata = metadata; }

    private Path resolveBackup(String filename) {
        if (filename == null || !filename.matches("backup-[a-f0-9-]{36}\\.zip")) {
            throw new IllegalArgumentException("Invalid backup name");
        }
        return backupPath.resolve(filename);
    }

    public synchronized String createBackup() throws IOException {
        String filename = "backup-" + UUID.randomUUID() + ".zip";
        Path destination = resolveBackup(filename);
        try (OutputStream output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW);
             ZipOutputStream zip = new ZipOutputStream(output);
             Stream<Path> paths = Files.walk(storagePath)) {
            List<Path> entries = paths.toList();
            long total = 0;
            if (entries.size() > MAX_ENTRIES) throw new IOException("Too many files");
            for (Path path : entries) {
                if (path.equals(storagePath)) continue;
                if (Files.isSymbolicLink(path)) throw new IOException("Symbolic links not supported");
                String name = storagePath.relativize(path).toString().replace('\\', '/');
                if (name.equals(BackupMetadataBridge.MANIFEST)) {
                    throw new IOException("Reserved filename in storage");
                }
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    zip.putNextEntry(new ZipEntry(name + "/"));
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    long size = Files.size(path);
                    if (size > MAX_ENTRY_BYTES || (total += size) > MAX_TOTAL_BYTES) {
                        throw new IOException("Backup exceeds configured size limit");
                    }
                    zip.putNextEntry(new ZipEntry(name));
                    Files.copy(path, zip);
                } else throw new IOException("Unsupported storage entry");
                zip.closeEntry();
            }
            if (metadata != null) {
                byte[] manifest = metadata.snapshot().getBytes(StandardCharsets.UTF_8);
                if (manifest.length > MAX_MANIFEST_BYTES) throw new IOException("Metadata too large");
                zip.putNextEntry(new ZipEntry(BackupMetadataBridge.MANIFEST));
                zip.write(manifest);
                zip.closeEntry();
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(destination);
            throw e;
        }
        return filename;
    }

    public List<String> listBackups() throws IOException {
        try (Stream<Path> files = Files.list(backupPath)) {
            return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("backup-[a-f0-9-]{36}\\.zip"))
                    .sorted().toList();
        }
    }

    public Path downloadBackup(String filename) throws IOException {
        Path backup = resolveBackup(filename);
        if (!Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)) throw new NoSuchFileException(filename);
        return backup;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path p : paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static void ensureSafeParents(Path root, Path destination) throws IOException {
        Path current = root;
        Path relative = root.relativize(destination);
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && Files.isSymbolicLink(current)) throw new IOException("Symbolic link in storage path");
        }
    }

    private static boolean sameFile(Path a, Path b) throws IOException {
        if (Files.size(a) != Files.size(b)) return false;
        try (InputStream x = Files.newInputStream(a); InputStream y = Files.newInputStream(b)) {
            byte[] bx = new byte[8192], by = new byte[8192];
            int n;
            while ((n = x.read(bx)) != -1) {
                int off = 0;
                while (off < n) {
                    int got = y.read(by, off, n - off);
                    if (got == -1) return false;
                    off += got;
                }
                if (!java.util.Arrays.equals(bx, 0, n, by, 0, n)) return false;
            }
            return y.read() == -1;
        }
    }

    // Non-destructive restore: existing identical files are preserved; conflicts abort.
    public synchronized String restoreBackup(String filename) throws IOException {
        Path source = downloadBackup(filename);
        Path staging = Files.createTempDirectory(backupPath, ".restore-stage-");
        List<Path> createdFiles = new ArrayList<>();
        List<Path> createdDirs = new ArrayList<>();
        try {
            String manifest = null;
            long total = 0;
            int count = 0;
            Set<String> seen = new HashSet<>();
            try (ZipFile archive = new ZipFile(source.toFile())) {
                var entries = archive.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (++count > MAX_ENTRIES) throw new IOException("Too many ZIP entries");
                    String name = entry.getName();
                    if (name.isBlank() || name.startsWith("/") || name.contains("\\")
                            || name.indexOf('\0') >= 0 || name.matches("^[A-Za-z]:.*")) {
                        throw new IOException("Unsafe ZIP path");
                    }
                    Path relative = Path.of(name).normalize();
                    if (relative.isAbsolute() || relative.startsWith("..") || relative.toString().equals(".")) {
                        throw new IOException("Unsafe ZIP path");
                    }
                    String normalized = relative.toString().replace('\\', '/');
                    if (!seen.add(normalized)) throw new IOException("Duplicate ZIP entry");
                    Path target = staging.resolve(relative).normalize();
                    if (!target.startsWith(staging)) throw new IOException("Unsafe ZIP entry");
                    if (normalized.equals(BackupMetadataBridge.MANIFEST)) {
                        if (entry.isDirectory() || entry.getSize() > MAX_MANIFEST_BYTES) {
                            throw new IOException("Invalid metadata manifest");
                        }
                        try (InputStream input = archive.getInputStream(entry)) {
                            byte[] bytes = input.readNBytes((int) MAX_MANIFEST_BYTES + 1);
                            if (bytes.length > MAX_MANIFEST_BYTES) throw new IOException("Metadata too large");
                            manifest = new String(bytes, StandardCharsets.UTF_8);
                        }
                        continue;
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        if (entry.getSize() > MAX_ENTRY_BYTES) throw new IOException("ZIP entry too large");
                        Files.createDirectories(target.getParent());
                        try (InputStream input = archive.getInputStream(entry);
                             OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                            CRC32 crc = new CRC32();
                            byte[] buffer = new byte[8192];
                            long entryBytes = 0;
                            int read;
                            while ((read = input.read(buffer)) != -1) {
                                entryBytes += read;
                                total += read;
                                if (entryBytes > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
                                    throw new IOException("ZIP exceeds size limit");
                                }
                                output.write(buffer, 0, read);
                                crc.update(buffer, 0, read);
                            }
                            if (entry.getCrc() != -1 && crc.getValue() != entry.getCrc()) {
                                throw new IOException("ZIP checksum mismatch");
                            }
                        }
                    }
                }
            }
            // ZIP fully extracted and CRC checked before touching uploads/.
            List<BackupMetadataBridge.Record> records = metadata == null ? List.of()
                    : metadata.completeSnapshot(
                            manifest == null ? List.of() : metadata.parse(manifest), staging);
            if (metadata != null) metadata.validate(records, staging, storagePath);
            try (Stream<Path> walk = Files.walk(staging)) {
                for (Path path : walk.toList()) {
                    if (path.equals(staging)) continue;
                    Path target = storagePath.resolve(staging.relativize(path)).normalize();
                    if (!target.startsWith(storagePath)) throw new IOException("Unsafe destination");
                    ensureSafeParents(storagePath, target);
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                        if (Files.isDirectory(path) && Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) continue;
                        if (Files.isRegularFile(path) && Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                                && sameFile(path, target)) continue;
                        throw new FileAlreadyExistsException("Conflicting file: " + storagePath.relativize(target));
                    }
                }
            }
            // Copy only missing entries, tracking them for rollback on failure.
            try (Stream<Path> walk = Files.walk(staging)) {
                for (Path path : walk.toList()) {
                    if (path.equals(staging)) continue;
                    Path target = storagePath.resolve(staging.relativize(path));
                    ensureSafeParents(storagePath, target);
                    if (Files.isDirectory(path)) {
                        if (Files.notExists(target, LinkOption.NOFOLLOW_LINKS)) {
                            Files.createDirectory(target);
                            createdDirs.add(target);
                        }
                    } else if (Files.notExists(target, LinkOption.NOFOLLOW_LINKS)) {
                        Files.copy(path, target);
                        createdFiles.add(target);
                    }
                }
            }
            List<String> warnings = new ArrayList<>();
            if (metadata != null) {
                Set<String> recovered = new HashSet<>();
                for (Path created : createdFiles)
                    recovered.add(storagePath.relativize(created).toString().replace('\\', '/'));
                warnings.addAll(metadata.restore(records, recovered));
                if (manifest == null || manifest.startsWith("NAS-METADATA-1\n"))
                    warnings.add("This older backup contains no sharing snapshot; old shares cannot be recovered.");
            }
            return "Backup restored successfully"
                + (warnings.isEmpty() ? "" : ". " + String.join("; ", warnings));
        } catch (IOException | RuntimeException e) {
            for (int i = createdFiles.size() - 1; i >= 0; i--) {
                try { Files.deleteIfExists(createdFiles.get(i)); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            }
            for (int i = createdDirs.size() - 1; i >= 0; i--) {
                try { Files.deleteIfExists(createdDirs.get(i)); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            }
            throw e;
        } finally {
            // Staging is temporary; deletion errors should be surfaced for inspection.
            deleteTree(staging);
        }
    }

    public synchronized void deleteBackup(String filename) throws IOException {
        Files.delete(downloadBackup(filename));
    }
}
