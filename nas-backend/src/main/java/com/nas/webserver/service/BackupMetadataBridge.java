package com.nas.webserver.service;

import com.nas.webserver.model.FileMetadata;
import com.nas.webserver.repository.FileMetadataRepository;
import com.nas.webserver.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class BackupMetadataBridge {
    static final String MANIFEST = ".nas-metadata.tsv";
    private final FileMetadataRepository metadata;
    private final UserRepository users;

    public BackupMetadataBridge(FileMetadataRepository metadata, UserRepository users) {
        this.metadata = metadata;
        this.users = users;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString((value == null ? "" : value)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
    }

    // Metadata is included in NEW ZIPs, not written into uploads/.
    public String snapshot() {
        StringBuilder text = new StringBuilder("NAS-METADATA-1\n");
        for (FileMetadata item : metadata.findAll()) {
            text.append(encode(item.getStoragePath())).append('\t')
                    .append(encode(item.getOriginalName())).append('\t')
                    .append(item.getFileSize()).append('\t')
                    .append(encode(item.getContentType())).append('\t')
                    .append(encode(item.getUploadedBy())).append('\t')
                    .append(item.getUploadedAt() == null ? "" : item.getUploadedAt())
                    .append('\n');
        }
        return text.toString();
    }

    public record Record(String path, String name, long size, String type,
                         String uploader, LocalDateTime uploadedAt) {}

    public List<Record> parse(String snapshot) throws IOException {
        if (snapshot == null) return List.of(); // Older backups have no manifest.
        String[] lines = snapshot.split("\\R", -1);
        if (lines.length == 0 || !lines[0].equals("NAS-METADATA-1")) {
            throw new IOException("Invalid metadata manifest");
        }
        List<Record> records = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try {
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].isEmpty()) continue;
                String[] fields = lines[i].split("\\t", -1);
                if (fields.length != 6) throw new IOException("Invalid metadata row");
                String path = decode(fields[0]);
                String name = decode(fields[1]);
                long size = Long.parseLong(fields[2]);
                if (!seen.add(path) || size < 0 || name.isBlank() || name.length() > 255) {
                    throw new IOException("Invalid or duplicate metadata path");
                }
                records.add(new Record(path, name, size, decode(fields[3]), decode(fields[4]),
                        fields[5].isEmpty() ? null : LocalDateTime.parse(fields[5])));
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid metadata manifest", e);
        }
        return records;
    }

    // Older ZIPs lack metadata; reconstruct basic records for user files.
    public List<Record> reconstructLegacy(Path staged) throws IOException {
        List<Record> records = new ArrayList<>();
        Path accounts = staged.resolve(".accounts");
        if (!Files.isDirectory(accounts)) return records;
        try (var paths = Files.walk(accounts)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                String path = staged.relativize(file).toString().replace('\\', '/');
                String[] parts = path.split("/");
                if (parts.length < 3 || !parts[1].matches("[1-9][0-9]*")) {
                    throw new IOException("Invalid account path in legacy backup");
                }
                long id;
                try { id = Long.parseLong(parts[1]); }
                catch (NumberFormatException e) { throw new IOException("Invalid account ID", e); }
                var user = users.findById(id).orElseThrow(
                        () -> new IOException("Backup references a missing user account"));
                records.add(new Record(path, file.getFileName().toString(), Files.size(file),
                        Files.probeContentType(file), user.getUsername(), LocalDateTime.now()));
            }
        }
        return records;
    }

    // Include every backed-up user file, even if an older upload lacks a DB row.
    // Existing manifest entries retain original uploader and timestamp.
    public List<Record> completeSnapshot(List<Record> manifestRecords, Path staged) throws IOException {
        List<Record> complete = new ArrayList<>(manifestRecords);
        Set<String> known = new HashSet<>();
        for (Record record : manifestRecords) known.add(record.path());
        for (Record fallback : reconstructLegacy(staged)) {
            if (known.add(fallback.path())) complete.add(fallback);
        }
        return complete;
    }

    // Validate metadata against staged file contents and existing user accounts.
    public void validate(List<Record> records, Path staged, Path storage) throws IOException {
        for (Record record : records) {
            String path = record.path();
            if (path.startsWith("/") || path.contains("\\") || path.contains("//")
                    || path.contains("/../") || path.contains("/./")
                    || path.endsWith("/..") || path.endsWith("/.")) {
                throw new IOException("Unsafe metadata path");
            }
            String[] parts = path.split("/");
            if (parts.length < 3 || !parts[0].equals(".accounts")
                    || !parts[1].matches("[1-9][0-9]*")) {
                throw new IOException("Invalid account metadata path");
            }
            long userId;
            try { userId = Long.parseLong(parts[1]); }
            catch (NumberFormatException e) { throw new IOException("Invalid user ID", e); }
            if (!users.existsById(userId)) throw new IOException("Backup references a missing user account");
            Path file = staged.resolve(path).normalize();
            if (!file.startsWith(staged) || !Files.isRegularFile(file)) {
                throw new IOException("Metadata references a missing backup file");
            }
            if (Files.size(file) != record.size()) throw new IOException("Metadata size mismatch");
            if (!file.getFileName().toString().equals(record.name())) {
                throw new IOException("Metadata filename mismatch");
            }
            if (metadata.findByStoragePath(path).isPresent()
                    && !Files.isRegularFile(storage.resolve(path))) {
                throw new IOException("Existing metadata conflicts with restored file");
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void restore(List<Record> records) {
        for (Record record : records) {
            if (metadata.findByStoragePath(record.path()).isPresent()) continue;
            FileMetadata item = new FileMetadata();
            item.setStoragePath(record.path());
            item.setOriginalName(record.name());
            item.setFileSize(record.size());
            item.setContentType(record.type().isBlank() ? null : record.type());
            item.setUploadedBy(record.uploader().isBlank() ? null : record.uploader());
            if (record.uploadedAt() != null) item.setUploadedAt(record.uploadedAt());
            metadata.save(item);
        }
        metadata.flush();
    }
}
