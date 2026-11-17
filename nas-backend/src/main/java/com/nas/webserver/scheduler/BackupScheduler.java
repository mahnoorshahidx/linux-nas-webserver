package com.nas.webserver.scheduler;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.nas.webserver.service.BackupService;

@Component
public class BackupScheduler {
    private final BackupService backupService;

    @Value("${nas.backup.interval-ms:86400000}")
    private long intervalMs = 86400000;

    public record Status(long intervalMs, boolean running,
                         Instant lastAttempt, Instant lastSuccess,
                         Instant nextExpectedRun, String lastBackup,
                         String lastError) {}

    private volatile Status state =
        new Status(86400000, false, null, null, null, null, null);

    public BackupScheduler(BackupService backupService) {
        this.backupService = backupService;
    }

    public Status status() {
        Status s = state;
        return new Status(intervalMs, s.running(), s.lastAttempt(),
            s.lastSuccess(), s.nextExpectedRun(), s.lastBackup(), s.lastError());
    }

    // Runs on startup, then waits the configured delay after completion.
    @Scheduled(fixedDelayString = "${nas.backup.interval-ms:86400000}")
    public void createAutomaticBackup() {
        Instant attempt = Instant.now();
        Status previous = state;
        state = new Status(intervalMs, true, attempt,
            previous.lastSuccess(), null, previous.lastBackup(), null);
        try {
            String filename = backupService.createBackup();
            Instant finished = Instant.now();
            state = new Status(intervalMs, false, attempt, finished,
                finished.plusMillis(intervalMs), filename, null);
            System.out.println("Automatic backup created: " + filename);
        } catch (java.io.IOException | RuntimeException e) {
            Instant finished = Instant.now();
            state = new Status(intervalMs, false, attempt,
                previous.lastSuccess(), finished.plusMillis(intervalMs),
                previous.lastBackup(),
                "Automatic backup failed; check server logs.");
            System.err.println("Automatic backup failed: " + e.getMessage());
        }
    }
}
