(() => {
    const node = document.getElementById("automaticBackupStatus");
    const time = value => value
        ? new Date(value).toLocaleString()
        : "Not recorded this session";

    function stat(label, value, state = false) {
        const card = document.createElement("div");
        card.className = "backup-stat";
        const heading = document.createElement("div");
        heading.className = "backup-stat-label";
        heading.textContent = label;
        const detail = document.createElement("div");
        detail.className = "backup-stat-value";
        if (state) {
            const pill = document.createElement("span");
            pill.className = "backup-state";
            pill.textContent = value;
            detail.append(pill);
        } else {
            detail.textContent = value;
        }
        card.append(heading, detail);
        return card;
    }

    async function refresh() {
        try {
            const response = await fetch("/api/backups/schedule", { cache: "no-store" });
            if (!response.ok) throw new Error("HTTP " + response.status);
            const s = await response.json();
            node.replaceChildren(
                stat("Current status", s.running ? "Running" : "Waiting", true),
                stat("Backup interval", (s.intervalMs / 3600000) + " hours after completion"),
                stat("Next expected run", s.running ? "After this run finishes" : time(s.nextExpectedRun)),
                stat("Last successful backup", time(s.lastSuccess)),
                stat("Last attempt", time(s.lastAttempt)),
                stat("Latest automatic ZIP", s.lastBackup || "No automatic backup yet")
            );
            if (s.lastError) {
                const error = document.createElement("p");
                error.className = "backup-schedule-error";
                error.textContent = s.lastError;
                node.append(error);
            }
        } catch (e) {
            const error = document.createElement("p");
            error.className = "backup-schedule-error";
            error.textContent = "Unable to load automatic backup status: " + e.message;
            node.replaceChildren(error);
        }
    }

    document.getElementById("refreshButton").addEventListener("click", refresh);
    refresh();
    setInterval(refresh, 15000);
})();
