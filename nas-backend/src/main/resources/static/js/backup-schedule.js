(() => {
    const node = document.getElementById("automaticBackupStatus");
    const time = value => value
        ? new Date(value).toLocaleString()
        : "Not recorded this session";

    async function refresh() {
        try {
            const response = await fetch("/api/backups/schedule", {
                cache: "no-store"
            });
            if (!response.ok) throw new Error("HTTP " + response.status);
            const s = await response.json();
            node.textContent =
                "Schedule: " + (s.intervalMs / 3600000) +
                " hours after completion. " +
                "State: " + (s.running ? "Running" : "Waiting") + ". " +
                "Last attempt: " + time(s.lastAttempt) + ". " +
                "Last successful automatic backup: " +
                time(s.lastSuccess) + ". " +
                "Next expected run: " +
                (s.running ? "After this run finishes" :
                    time(s.nextExpectedRun)) + ". " +
                (s.lastBackup ? "Latest automatic ZIP: " +
                    s.lastBackup + ". " : "") +
                (s.lastError || "");
        } catch (e) {
            node.textContent =
                "Unable to load automatic backup status: " + e.message;
        }
    }

    document.getElementById("refreshButton")
        .addEventListener("click", refresh);
    refresh();
    setInterval(refresh, 15000);
})();
