package com.nas.webserver.controller;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.nas.webserver.service.SystemLogService;

@RestController
@RequestMapping("/api/monitoring")
public class SystemLogController {

    private final SystemLogService systemLogService;

    public SystemLogController(SystemLogService systemLogService) {
        this.systemLogService = systemLogService;
    }

    // Get the latest system journal entries
    @GetMapping("/logs")
    public ResponseEntity<Map<String, String>> getLogs() throws IOException {
        String logs = systemLogService.getRecentLogs();

        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(Map.of(
                        "timestamp", Instant.now().toString(),
                        "logs", logs
                ));
    }

    // Handle errors reading the journal
    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleLogError(IOException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("System logs are unavailable. Check journal permissions.");
    }
}
