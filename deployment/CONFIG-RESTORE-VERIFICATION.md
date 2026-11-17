# Ubuntu configuration restore verification

Verified on 2026-11-16 using test-integration commit be16584.
Work performed on ibrahim/config-restore-verification.

## Configuration paths

- Sources: ~/.local/state/linux-nas/config-sources/{apache,ufw}
- Archives: ~/.local/state/linux-nas/config-backups
- Staging: ~/.local/state/linux-nas/config-restore-staging
- Path environment settings: ~/.config/linux-nas/config-backup.env
- Protected rollback archive: /var/backups/linux-nas/

Sources contain copies of ports.conf, NAS HTTP/HTTPS virtual hosts,
eight UFW configuration files, and /etc/default/ufw.
Apache enabled site/module links are recorded in enabled-links.txt.

These are snapshots. Refresh source copies from the live configuration
before creating future backups when settings have changed.
The application does not automatically apply staged restores.
Private keys and database credentials are not included in these app archives.

## Application startup

Source both local environment files before starting Spring Boot:

```bash
cd ~/projects/linux-nas-webserver/nas-backend
source ~/.config/linux-nas/runtime.env
source ~/.config/linux-nas/config-backup.env
umask 077
SERVER_FORWARD_HEADERS_STRATEGY=framework \
SERVER_SERVLET_SESSION_COOKIE_SECURE=true \
bash ./mvnw spring-boot:run

```

## Verified results

- 57 selected service/controller tests passed.
- Configuration backup creation and staging succeeded.
- All 12 selected restored files matched the live settings.
- Restored copies were applied; Apache and UFW reloaded successfully.
- Apache and MySQL remained active.
- Dashboard and monitoring worked locally and over phone 5G.
- Windows forwarding on ports 8081 and 8443 worked.
- Public HTTPS access worked through Cloudflare Tunnel.

## Scope

This verified reapplying identical known-good settings, not recovery
from damaged settings or a complete server rebuild.
Certificate/key, application-secret, enabled-link reconstruction,
and VirtualBox configuration restoration were not tested.
Refresh source snapshots when live settings change.
Review future staged restores before applying them.

No application source changes or main-branch merges were made.
