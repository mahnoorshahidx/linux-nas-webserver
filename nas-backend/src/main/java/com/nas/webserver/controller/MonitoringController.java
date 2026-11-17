package com.nas.webserver.controller;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.nas.webserver.service.MonitoringService;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    private final MonitoringService monitoringService;

    public MonitoringController(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    // Get current system measurements
    @GetMapping("/metrics")
    public Map<String, Object> getMetrics() throws IOException {
        return monitoringService.getMetrics();
    }

    // Handle errors reading storage information
    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleMonitoringError(IOException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("System measurements could not be read");
    }
}
