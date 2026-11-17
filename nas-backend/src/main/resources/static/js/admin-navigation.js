async function loadAdminNavigation() {
    const nav = document.querySelector(".sidebar nav");
    if (!nav) return;

    function render(isAdmin) {
        const links = [
            ["/dashboard.html", "📊", "Dashboard", true],
            ["/monitoring.html", "📈", "System Monitoring", true],
            ["/admin-users.html", "👥", "User Management", true],
            ["/files.html", "📄", "My Files", false],
            ["/folders.html", "📁", "Folders", false],
            ["/backups.html", "💾", "Backup & Restore", true],
            ["/settings.html", "⚙️", "Settings", false]
        ];
        const fragment = document.createDocumentFragment();
        for (const [href, icon, label, adminOnly] of links) {
            if (adminOnly && !isAdmin) continue;
            const link = document.createElement("a");
            link.href = href;

            const span = document.createElement("span");
            span.className = "nav-icon";
            span.setAttribute("aria-hidden", "true");
            span.textContent = icon;
            link.append(span, document.createTextNode(" " + label));

            if (window.location.pathname === href) {
                link.classList.add("active");
                link.setAttribute("aria-current", "page");
            }
            fragment.append(link);
        }
        nav.replaceChildren(fragment);
    }

    // Keep privileged links hidden until the account is confirmed.
    render(false);
    try {
        const response = await fetch("/api/account/me", { cache: "no-store" });
        if (!response.ok) return;
        const account = await response.json();
        render(account.roleName === "ADMIN");
    } catch (error) {
        console.error("Unable to load admin navigation.");
    }
}

loadAdminNavigation();
