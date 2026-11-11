const refreshLogsButton = document.getElementById("refreshLogsButton");
const logsStatus = document.getElementById("logsStatus");
const logOutput = document.getElementById("logOutput");
let loadingLogs = false;

// Fetch a fresh snapshot when the page opens or Refresh is clicked.
async function loadLogs() {
    if (loadingLogs) return;
    loadingLogs = true;
    refreshLogsButton.disabled = true;
    logsStatus.textContent = "Loading system logs...";
    logsStatus.classList.remove("is-error");

    try {
        const response = await fetch("/api/monitoring/logs", {
            cache: "no-store",
            signal: AbortSignal.timeout(10000)
        });

        if (response.redirected || response.status === 401 ||
                response.status === 403) {
            throw new Error("Please sign in again, then reload this page.");
        }

        if (!response.ok) {
            throw new Error("System logs could not be loaded. Check the server and journal permissions.");
        }

        const data = await response.json();

        // Display logs as plain text, never as HTML.
        logOutput.textContent = data.logs.trim() || "No journal entries returned.";
        logOutput.scrollTop = logOutput.scrollHeight;
        logsStatus.textContent =
                "Updated " + new Date(data.timestamp).toLocaleTimeString() +
                " · Click Refresh for the latest entries";
    } catch (error) {
        logsStatus.classList.add("is-error");
        logsStatus.textContent =
                error.message + " Any displayed logs are from the previous successful request.";
    } finally {
        loadingLogs = false;
        refreshLogsButton.disabled = false;
    }
}

refreshLogsButton.addEventListener("click", loadLogs);
loadLogs();
