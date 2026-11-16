package com.nas.webserver.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Service;

@Service
public class SystemLogService {

    // Read a bounded snapshot of the system journal
    public String getRecentLogs() throws IOException {
        Process process = new ProcessBuilder(
                "/usr/bin/journalctl",
                "--system",
                "-n", "50",
                "--no-pager",
                "-o", "short-iso"
        ).redirectErrorStream(true).start();

        // Read concurrently so a stalled command cannot block the request forever
        FutureTask<byte[]> output = new FutureTask<>(() ->
                process.getInputStream().readNBytes(65537));
        Thread.ofVirtual().start(output);

        try {
            byte[] bytes = output.get(5, TimeUnit.SECONDS);

            if (bytes.length > 65536) {
                return new String(bytes, 0, 65536, StandardCharsets.UTF_8)
                        + "\n[Output truncated to 64 KiB]";
            }

            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                throw new IOException("System journal timed out");
            }

            if (process.exitValue() != 0) {
                throw new IOException("System journal could not be read");
            }

            return new String(bytes, StandardCharsets.UTF_8);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("System journal request interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("System journal could not be read", e);
        } finally {
            process.destroyForcibly();
            output.cancel(true);
        }
    }
}
