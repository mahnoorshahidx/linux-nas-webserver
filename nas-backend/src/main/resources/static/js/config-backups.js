// Configuration backups are separate from user-file backups.
const configList = document.getElementById("configBackupList");
const configStatus = document.getElementById("configBackupStatus");
async function configRequest(url, method = "GET") {
    const response = await fetchWithCsrf(url, { method });
    if (!response.ok) throw new Error(await response.text());
    return method === "GET" ? response.json() : response.text();
}
async function refreshConfigs() {
    try {
        const names = await configRequest("/api/backups/config");
        configList.replaceChildren();
        if (!names.length) configList.textContent = "No configuration backups yet.";
        for (const name of names) {
            const item = document.createElement("li");
            item.append(document.createTextNode(name + " "));
            const button = document.createElement("button");
            button.type = "button";
            button.className = "secondary-btn";
            button.textContent = "Stage Restore";
            button.addEventListener("click", async () => {
                if (!confirm("Stage configuration files for administrator review? Live settings will not be changed.")) return;
                try {
                    configStatus.textContent = await configRequest(
                        "/api/backups/config/" + encodeURIComponent(name) + "/stage", "POST");
                } catch (e) { configStatus.textContent = e.message; }
            });
            item.append(button);
            configList.append(item);
        }
    } catch (e) { configStatus.textContent = e.message; }
}
document.getElementById("createConfigBackup").addEventListener("click", async () => {
    try {
        const name = await configRequest("/api/backups/config", "POST");
        configStatus.textContent = "Created " + name;
        await refreshConfigs();
    } catch (e) { configStatus.textContent = e.message; }
});
document.getElementById("refreshConfigBackups").addEventListener("click", refreshConfigs);
refreshConfigs();
