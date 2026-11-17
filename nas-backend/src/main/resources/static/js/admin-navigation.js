async function loadAdminNavigation() {
    try {
        const response = await fetch("/api/account/me", {
            cache: "no-store"
        });
        if (!response.ok) return;

        const account = await response.json();
        if (account.roleName !== "ADMIN") return;

        const nav = document.querySelector(".sidebar nav");
        if (!nav) return;

        const links = [
            ["/dashboard.html", "Dashboard"],
            ["/monitoring.html", "System Monitoring"],
            ["/admin-users.html", "User Management"]
        ];

        const fragment = document.createDocumentFragment();
        for (const [href, label] of links) {
            if (nav.querySelector('a[href="' + href + '"]')) continue;
            const link = document.createElement("a");
            link.href = href;
            link.textContent = label;
            if (window.location.pathname === href) {
                link.classList.add("active");
                link.setAttribute("aria-current", "page");
            }
            fragment.append(link);
        }
        nav.prepend(fragment);

        if (!nav.querySelector('a[href="/settings.html"]')) {
            const settings = document.createElement("a");
            settings.href = "/settings.html";
            settings.textContent = "Settings";
            nav.append(settings);
        }
    } catch (error) {
        console.error("Unable to load admin navigation.");
    }
}

loadAdminNavigation();
