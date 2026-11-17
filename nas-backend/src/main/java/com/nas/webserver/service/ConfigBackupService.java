package com.nas.webserver.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Creates reviewed, staged configuration restores; never changes live /etc files. */
@Service
public class ConfigBackupService {
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final int MAX_FILES = 300;
    private final Path backupDir;
    private final Path stageDir;
    private final Map<String, Path> sources = new LinkedHashMap<>();

    public ConfigBackupService(
            @Value("${nas.config-backup.sources:}") String sourceList,
            @Value("${nas.config-backup.path:config-backups}") String backupLocation,
            @Value("${nas.config-backup.stage-path:config-restore-staging}") String stageLocation) throws IOException {
        backupDir = Paths.get(backupLocation).toAbsolutePath().normalize();
        stageDir = Paths.get(stageLocation).toAbsolutePath().normalize();
        if (backupDir.equals(stageDir) || backupDir.startsWith(stageDir) || stageDir.startsWith(backupDir))
            throw new IllegalArgumentException("Configuration backup and staging paths must be separate");
        Files.createDirectories(backupDir);
        Files.createDirectories(stageDir);
        if (Files.isSymbolicLink(backupDir) || Files.isSymbolicLink(stageDir))
            throw new IOException("Backup and staging folders cannot be symlinks");
        // Explicit admin-controlled list: apache=/etc/apache2/sites-available,ufw=/etc/ufw
        for (String pair : sourceList.split(",")) {
            if (pair.isBlank()) continue;
            String[] fields = pair.trim().split("=", 2);
            if (fields.length != 2 || !fields[0].matches("[a-z][a-z0-9-]{0,30}"))
                throw new IllegalArgumentException("Invalid configuration source mapping");
            if (sources.containsKey(fields[0])) throw new IllegalArgumentException("Duplicate source label");
            Path path = Paths.get(fields[1].trim()).toAbsolutePath().normalize();
            if (!path.isAbsolute() || path.startsWith(backupDir) || path.startsWith(stageDir))
                throw new IllegalArgumentException("Invalid configuration source path");
            sources.put(fields[0], path);
        }
    }

    private Path archive(String name) {
        if (name == null || !name.matches("config-[a-f0-9-]{36}\\.zip"))
            throw new IllegalArgumentException("Invalid configuration backup name");
        return backupDir.resolve(name);
    }

    private boolean excluded(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".key") || name.endsWith(".p12") || name.endsWith(".pfx")
                || name.equals(".env") || name.contains("private") || name.contains("secret")
                || name.contains("password") || name.equals("application.properties");
    }

    public synchronized String create() throws IOException {
        if (sources.isEmpty()) throw new IllegalStateException("No configuration sources configured by server administrator");
        String name = "config-" + UUID.randomUUID() + ".zip";
        Path destination = archive(name);
        long total = 0;
        int count = 0;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW))) {
            for (var source : sources.entrySet()) {
                Path root = source.getValue();
                if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) throw new NoSuchFileException(root.toString());
                if (Files.isSymbolicLink(root)) throw new IOException("Symlink configuration source not allowed");
                try (Stream<Path> paths = Files.isDirectory(root) ? Files.walk(root) : Stream.of(root)) {
                    for (Path file : paths.toList()) {
                        if (Files.isSymbolicLink(file)) throw new IOException("Symlink in configuration source");
                        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || excluded(file)) continue;
                        if (++count > MAX_FILES || (total += Files.size(file)) > MAX_BYTES)
                            throw new IOException("Configuration backup size/count limit exceeded");
                        String relative = Files.isDirectory(root) ? root.relativize(file).toString() : root.getFileName().toString();
                        String entry = source.getKey() + "/" + relative.replace('\\', '/');
                        zip.putNextEntry(new ZipEntry(entry));
                        Files.copy(file, zip);
                        zip.closeEntry();
                    }
                }
            }
        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(destination);
            throw ex;
        }
        return name;
    }

    public List<String> list() throws IOException {
        try (Stream<Path> files = Files.list(backupDir)) {
            return files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.matches("config-[a-f0-9-]{36}\\.zip")).sorted().toList();
        }
    }

    public record StagedRestore(String id, String backup,
                                java.time.Instant completedAt) {}

    public synchronized List<StagedRestore> staged() throws IOException {
        List<StagedRestore> result = new ArrayList<>();
        try (Stream<Path> paths = Files.list(stageDir)) {
            for (Path p : paths.sorted().toList()) {
                if (!p.getFileName().toString().matches("restore-[a-f0-9-]{36}")
                        || !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) continue;
                Path marker = p.resolve(".stage-source");
                if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                        || Files.size(marker) > 100) continue;
                String backup = Files.readString(marker);
                if (!backup.matches("config-[a-f0-9-]{36}\\.zip")) continue;
                result.add(new StagedRestore(p.getFileName().toString(),
                    backup, Files.getLastModifiedTime(marker).toInstant()));
            }
        }
        return result;
    }

    /** Extract only to a fresh staging directory for review by Linux administrator. */
    public synchronized String stage(String name) throws IOException {
        Path file = archive(name);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new NoSuchFileException(name);
        String stageId = "restore-" + UUID.randomUUID();
        Path target = stageDir.resolve(stageId);
        Files.createDirectory(target);
        try (ZipFile zip = new ZipFile(file.toFile())) {
            long total = 0;
            int count = 0;
            Set<String> seen = new HashSet<>();
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                if (++count > MAX_FILES) throw new IOException("Too many configuration entries");
                String entryName = entry.getName();
                if (entryName.contains("\\") || entryName.startsWith("/") || !seen.add(entryName))
                    throw new IOException("Invalid configuration archive entry");
                Path relative = Paths.get(entryName).normalize();
                if (relative.getNameCount() < 2 || relative.startsWith("..")
                        || !sources.containsKey(relative.getName(0).toString()))
                    throw new IOException("Unexpected configuration path");
                Path dest = target.resolve(relative).normalize();
                if (!dest.startsWith(target) || excluded(dest)) throw new IOException("Unsafe configuration entry");
                Files.createDirectories(dest.getParent());
                try (InputStream in = zip.getInputStream(entry);
                     OutputStream output = Files.newOutputStream(dest, StandardOpenOption.CREATE_NEW)) {
                    byte[] buf = new byte[8192];
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        total += read;
                        if (total > MAX_BYTES) throw new IOException("Configuration archive too large");
                        output.write(buf, 0, read);
                    }
                }
            }
            Files.writeString(target.resolve(".stage-source"), name,
                StandardOpenOption.CREATE_NEW);
        } catch (IOException | RuntimeException ex) {
            try (Stream<Path> paths = Files.walk(target)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
            throw ex;
        }
        return stageId;
    }
}
