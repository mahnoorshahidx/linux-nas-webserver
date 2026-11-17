(() => {
    const list = document.getElementById("configBackupList");
    const status = document.getElementById("configBackupStatus");
    const stages = document.getElementById("configStageList");

    function message(text, error = false) {
        status.textContent = text;
        if (typeof showToast === "function") showToast(text, error);
    }

    async function request(url, method = "GET") {
        const response = await fetchWithCsrf(url, {
            method, cache: "no-store"
        });
        if (!response.ok) {
            throw new Error(await response.text() ||
                "Request failed: " + response.status);
        }
        return method === "GET" ? response.json() : response.text();
    }

    async function loadStages() {
        const records = await request("/api/backups/config/staged");
        stages.replaceChildren();
        if (!records.length) {
            stages.textContent = "No completed stages recorded yet.";
        }
        for (const record of records) {
            const li = document.createElement("li");
            li.textContent = record.id + " — " + record.backup + " — " +
                new Date(record.completedAt).toLocaleString() +
                " — awaiting administrator review";
            stages.append(li);
        }
    }

    async function refresh() {
        try {
            const names = await request("/api/backups/config");
            list.replaceChildren();
            if (!names.length) {
                list.textContent = "No configuration backups yet.";
            }
            for (const name of names) {
                const li = document.createElement("li");
                li.append(document.createTextNode(name + " "));
                const button = document.createElement("button");
                button.type = "button";
                button.className = "secondary-btn";
                button.textContent = "Stage Restore";
                button.addEventListener("click", async () => {
                    button.disabled = true;
                    status.textContent =
                        "Staging for review. Live settings will not change...";
                    try {
                        const result = await request(
                            "/api/backups/config/" +
                            encodeURIComponent(name) + "/stage", "POST");
                        message(result);
                        await loadStages();
                    } catch (e) {
                        message(e.message, true);
                    } finally {
                        button.disabled = false;
                    }
                });
                li.append(button);
                list.append(li);
            }
            await loadStages();
        } catch (e) {
            message(e.message, true);
        }
    }

    const create = document.getElementById("createConfigBackup");
    create.addEventListener("click", async () => {
        create.disabled = true;
        try {
            const name = await request("/api/backups/config", "POST");
            message("Created " + name);
            await refresh();
        } catch (e) {
            message(e.message, true);
        } finally {
            create.disabled = false;
        }
    });

    document.getElementById("refreshConfigBackups")
        .addEventListener("click", refresh);
    refresh();
})();
