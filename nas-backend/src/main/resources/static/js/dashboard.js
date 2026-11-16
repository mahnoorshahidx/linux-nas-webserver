const refreshButton = document.getElementById("refreshButton");
const dashboardStatus = document.getElementById("dashboardStatus");
let loading = false;

function showText(id, value) {
    document.getElementById(id).textContent = value;
}

function formatGiB(bytes) {
    return (bytes / (1024 ** 3)).toFixed(1) + " GiB";
}

function formatPercent(used, total) {
    return total > 0 ? (used / total * 100).toFixed(1) + "%" : "Unavailable";
}

function formatUptime(seconds) {
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor(seconds % 86400 / 3600);
    const minutes = Math.floor(seconds % 3600 / 60);
    return `${days}d ${hours}h ${minutes}m`;
}

// Request measurements without reloading the whole page.
async function loadMetrics() {
    if (loading) return;
    loading = true;
    refreshButton.disabled = true;

    try {
        const response = await fetch("/api/monitoring/metrics", {
            cache: "no-store",
            signal: AbortSignal.timeout(10000)
        });

        if (response.redirected || response.status === 401 ||
                response.status === 403) {
            throw new Error("Please sign in again, then reload this page.");
        }

        if (!response.ok) {
            throw new Error("Could not load measurements. Check the server and retry.");
        }

        const metrics = await response.json();

        showText("cpuValue", metrics.cpuPercent === null
                ? "Unavailable" : metrics.cpuPercent.toFixed(1) + "%");
        showText("cpuDetails", `${metrics.cpuCores} logical CPU core(s)`);

        showText("memoryValue",
                formatPercent(metrics.memoryUsedBytes, metrics.memoryTotalBytes));
        showText("memoryDetails",
                `${formatGiB(metrics.memoryUsedBytes)} used / ${formatGiB(metrics.memoryTotalBytes)} total`);

        showText("diskValue",
                formatPercent(metrics.diskUsedBytes, metrics.diskTotalBytes));
        showText("diskDetails",
                `${formatGiB(metrics.diskUsedBytes)} used / ${formatGiB(metrics.diskTotalBytes)} total; ${formatGiB(metrics.diskAvailableBytes)} available`);

        showText("uptimeValue", formatUptime(metrics.applicationUptimeSeconds));

        dashboardStatus.classList.remove("is-error");
        dashboardStatus.textContent =
                "Updated " + new Date(metrics.timestamp).toLocaleTimeString() +
                " · Refreshes every 5 seconds";
    } catch (error) {
        dashboardStatus.classList.add("is-error");
        dashboardStatus.textContent =
                error.message + " Any displayed measurements are from the last successful update.";
    } finally {
        loading = false;
        refreshButton.disabled = false;
    }
}

refreshButton.addEventListener("click", loadMetrics);
loadMetrics();
setInterval(loadMetrics, 5000);
