package com.nas.webserver.controller;

import com.nas.webserver.service.ConfigBackupService;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/backups/config")
public class ConfigBackupController {
    private final ConfigBackupService service;
    public ConfigBackupController(ConfigBackupService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<String> create() throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create());
    }

    @GetMapping
    public List<String> list() throws IOException { return service.list(); }

    @GetMapping("/staged")
    public List<ConfigBackupService.StagedRestore> staged() throws IOException {
        return service.staged();
    }

    @PostMapping("/{filename}/stage")
    public String stage(@PathVariable String filename) throws IOException {
        return "Configuration files staged for Linux administrator review: " + service.stage(filename);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badName(Exception e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> notConfigured(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler(NoSuchFileException.class)
    public ResponseEntity<String> notFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Configuration backup or source not found");
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> failed(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Configuration backup failed: " + e.getMessage());
    }
}
