package com.nas.webserver.controller;

import com.nas.webserver.scheduler.BackupScheduler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Existing security rules restrict /api/backups/** to administrators.
@RestController
public class BackupScheduleController {
    private final BackupScheduler scheduler;

    public BackupScheduleController(BackupScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @GetMapping("/api/backups/schedule")
    public BackupScheduler.Status status() {
        return scheduler.status();
    }
}
