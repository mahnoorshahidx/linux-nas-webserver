package com.nas.webserver.service;

import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileStore;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MonitoringService {

    private final OperatingSystemMXBean operatingSystem =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    private final Path storagePath;

    public MonitoringService(
            @Value("${nas.storage.path:uploads}") String storagePath) {
        this.storagePath = Path.of(storagePath).toAbsolutePath().normalize();
    }

    // Collect CPU, memory, disk, and application uptime measurements
    public Map<String, Object> getMetrics() throws IOException {
        Map<String, Object> metrics = new LinkedHashMap<>();

        double cpuLoad = operatingSystem.getCpuLoad();
        long totalMemory = operatingSystem.getTotalMemorySize();
        long freeMemory = operatingSystem.getFreeMemorySize();

        // Find the filesystem containing the upload directory
        Path existingPath = storagePath;
        while (!Files.exists(existingPath) && existingPath.getParent() != null) {
            existingPath = existingPath.getParent();
        }

        FileStore disk = Files.getFileStore(existingPath);
        long totalDisk = disk.getTotalSpace();
        long freeDisk = disk.getUnallocatedSpace();

        metrics.put("timestamp", Instant.now().toString());
        metrics.put("cpuPercent",
                cpuLoad < 0 ? null : Math.round(cpuLoad * 1000.0) / 10.0);
        metrics.put("cpuCores", operatingSystem.getAvailableProcessors());
        metrics.put("memoryTotalBytes", totalMemory);
        metrics.put("memoryUsedBytes", totalMemory - freeMemory);
        metrics.put("diskTotalBytes", totalDisk);
        metrics.put("diskUsedBytes", totalDisk - freeDisk);
        metrics.put("diskAvailableBytes", disk.getUsableSpace());
        metrics.put("applicationUptimeSeconds",
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000);

        return metrics;
    }
}
