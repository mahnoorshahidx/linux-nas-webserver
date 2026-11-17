// Inline, authenticated file-sharing panel. No browser prompts or popups.
(async function () {
    const body = document.getElementById('sharingList');
    const status = document.getElementById('sharingMessage');
    const panel = document.getElementById('sharePanel');
    if (!body || !panel) return;
    const $ = id => document.getElementById(id);
    let selectedFile = null;
    let foundUser = null;
    let lookupVersion = 0;

    async function request(url, options = {}) {
        const method = (options.method || 'GET').toUpperCase();
        if (method !== 'GET') {
            const response = await fetch('/api/csrf', {cache:'no-store'});
            if (!response.ok) throw new Error('Session expired. Please log in again.');
            const csrf = await response.json();
            const headers = new Headers(options.headers || {});
            headers.set(csrf.headerName, csrf.token);
            options.headers = headers;
        }
        const response = await fetch(url, {cache:'no-store', ...options});
        if (!response.ok) {
            const message = await response.text();
            throw new Error(response.status === 404 ? (message || 'User not found') : (message || `Request failed (${response.status})`));
        }
        return response.status === 204 ? null : response.json();
    }
    function setMessage(node, message, error = false) {
        node.textContent = message;
        node.classList.toggle('share-error', error);
    }
    function permissionButtons(container, values = {canRead:true, canWrite:false, canEdit:false}) {
        const state = {...values};
        container.replaceChildren();
        for (const [key, label] of [['canRead','Read'], ['canWrite','Write'], ['canEdit','Edit']]) {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'share-toggle';
            button.textContent = label;
            button.setAttribute('aria-pressed', String(!!state[key]));
            button.addEventListener('click', () => {
                state[key] = !state[key];
                button.setAttribute('aria-pressed', String(state[key]));
            });
            container.append(button);
        }
        return state;
    }
    async function loadAccess() {
        if (!selectedFile) return;
        const fileId = selectedFile.id;
        const list = $('shareAccessList');
        list.textContent = 'Loading access list…';
        const entries = await request(`/api/sharing/files/${encodeURIComponent(fileId)}/access`);
        if (!selectedFile || selectedFile.id !== fileId) return;
        list.replaceChildren();
        if (!entries.length) list.textContent = 'No access records found.';
        for (const entry of entries) {
            const card = document.createElement('div');
            card.className = 'share-person';
            const info = document.createElement('div');
            info.className = 'share-person-info';
            const name = document.createElement('strong');
            name.textContent = entry.username + (entry.owner ? ' (Owner)' : '');
            const email = document.createElement('span');
            email.textContent = entry.email;
            info.append(name, email);
            card.append(info);
            if (entry.owner) {
                const tag = document.createElement('span');
                tag.className = 'share-owner-tag';
                tag.textContent = 'Full access';
                card.append(tag);
            } else {
                const controls = document.createElement('div');
                controls.className = 'share-person-controls';
                const buttons = document.createElement('div');
                buttons.className = 'share-permission-row';
                const state = permissionButtons(buttons, entry);
                const save = document.createElement('button');
                save.type = 'button';
                save.className = 'secondary-btn';
                save.textContent = 'Save';
                const remove = document.createElement('button');
                remove.type = 'button';
                remove.className = 'share-remove';
                remove.textContent = 'Remove';
                save.addEventListener('click', async () => {
                    save.disabled = true;
                    try {
                        await request(`/api/sharing/files/${fileId}/permissions`, {
                            method:'PUT', headers:{'Content-Type':'application/json'},
                            body:JSON.stringify({username:entry.username, ...state})
                        });
                        setMessage($('sharePanelStatus'), `Updated access for ${entry.username}.`);
                        await loadAccess();
                    } catch (e) { setMessage($('sharePanelStatus'), e.message, true); save.disabled = false; }
                });
                remove.addEventListener('click', async () => {
                    remove.disabled = true;
                    try {
                        await request(`/api/sharing/files/${fileId}/permissions/${entry.userId}`, {method:'DELETE'});
                        setMessage($('sharePanelStatus'), `Removed access for ${entry.username}.`);
                        await loadAccess();
                    } catch (e) { setMessage($('sharePanelStatus'), e.message, true); remove.disabled = false; }
                });
                controls.append(buttons, save, remove);
                card.append(controls);
            }
            list.append(card);
        }
    }
    async function openPanel(file) {
        selectedFile = file;
        foundUser = null;
        lookupVersion++;
        panel.hidden = false;
        $('sharePanelTitle').textContent = 'Share file';
        $('shareFilename').textContent = file.name;
        $('shareUserQuery').value = '';
        $('shareNewUser').hidden = true;
        setMessage($('shareLookupStatus'), '');
        setMessage($('sharePanelStatus'), '');
        panel.scrollIntoView({behavior:'smooth', block:'nearest'});
        try { await loadAccess(); } catch (e) { setMessage($('sharePanelStatus'), e.message, true); }
    }
    $('closeSharePanel').addEventListener('click', () => {
        panel.hidden = true;
        selectedFile = null;
        foundUser = null;
        lookupVersion++;
    });
    $('shareUserQuery').addEventListener('input', () => {
        foundUser = null;
        lookupVersion++;
        $('shareNewUser').hidden = true;
        setMessage($('shareLookupStatus'), '');
    });
    $('shareUserQuery').addEventListener('keydown', e => {
        if (e.key === 'Enter') { e.preventDefault(); $('shareLookupButton').click(); }
    });
    $('shareLookupButton').addEventListener('click', async () => {
        if (!selectedFile) return;
        const query = $('shareUserQuery').value.trim();
        if (!query) { setMessage($('shareLookupStatus'), 'Enter a username or email.', true); return; }
        const version = ++lookupVersion;
        const fileId = selectedFile.id;
        foundUser = null;
        $('shareNewUser').hidden = true;
        setMessage($('shareLookupStatus'), 'Looking for user…');
        try {
            const user = await request(`/api/sharing/files/${fileId}/lookup?query=${encodeURIComponent(query)}`);
            if (version !== lookupVersion || !selectedFile || selectedFile.id !== fileId) return;
            const existing = await request(`/api/sharing/files/${fileId}/access`);
            if (version !== lookupVersion) return;
            if (existing.some(item => item.userId === user.id)) {
                setMessage($('shareLookupStatus'), `${user.username} already has access to this file.`);
                return;
            }
            foundUser = user;
            $('shareNewUserName').textContent = `${user.username} (${user.email})`;
            permissionButtons($('shareNewPermissions'));
            $('shareNewUser').hidden = false;
            setMessage($('shareLookupStatus'), `User found: ${user.username}`);
        } catch (e) {
            if (version === lookupVersion) setMessage($('shareLookupStatus'), e.message, true);
        }
    });
    $('shareAddButton').addEventListener('click', async () => {
        if (!foundUser || !selectedFile) return;
        const selected = {};
        for (const [index, key] of ['canRead','canWrite','canEdit'].entries()) {
            selected[key] = $('shareNewPermissions').children[index].getAttribute('aria-pressed') === 'true';
        }
        if (!Object.values(selected).some(Boolean)) {
            setMessage($('sharePanelStatus'), 'Choose at least one permission.', true);
            return;
        }
        const user = foundUser;
        const fileId = selectedFile.id;
        $('shareAddButton').disabled = true;
        try {
            await request(`/api/sharing/files/${fileId}/permissions`, {
                method:'PUT', headers:{'Content-Type':'application/json'},
                body:JSON.stringify({username:user.username, ...selected})
            });
            foundUser = null;
            $('shareNewUser').hidden = true;
            $('shareUserQuery').value = '';
            setMessage($('shareLookupStatus'), '');
            setMessage($('sharePanelStatus'), `Shared file with ${user.username}.`);
            await loadAccess();
        } catch (e) { setMessage($('sharePanelStatus'), e.message, true); }
        finally { $('shareAddButton').disabled = false; }
    });
    async function refresh() {
        const files = await request('/api/sharing/files');
        body.replaceChildren();
        status.textContent = files.length ? '' : 'No file metadata available yet.';
        for (const file of files) {
            const tr = body.insertRow();
            tr.insertCell().textContent = file.name;
            tr.insertCell().textContent = file.owner || 'Unknown';
            tr.insertCell().textContent = file.mine ? 'Owned by you' : 'Shared with you';
            const cell = tr.insertCell();
            const download = document.createElement('a');
            download.href = `/api/sharing/files/${encodeURIComponent(file.id)}/download`;
            download.textContent = 'Download';
            cell.append(download);
            if (file.mine) {
                const share = document.createElement('button');
                share.type = 'button';
                share.className = 'secondary-btn';
                share.textContent = 'Share';
                share.addEventListener('click', () => openPanel(file));
                cell.append(' ', share);
                const del = document.createElement('button');
                del.type = 'button';
                del.className = 'share-remove';
                del.textContent = 'Delete';
                del.setAttribute('aria-label', `Delete ${file.name}`);
                del.addEventListener('click', () => {
                    // Inline confirmation, without navigating away or opening a popup.
                    if (cell.querySelector('.share-delete-confirm')) return;
                    const confirmation = document.createElement('div');
                    confirmation.className = 'share-delete-confirm';
                    const warning = document.createElement('span');
                    warning.textContent = `Permanently delete ${file.name}?`;
                    const yes = document.createElement('button');
                    yes.type = 'button';
                    yes.className = 'share-remove';
                    yes.textContent = 'Confirm delete';
                    const cancel = document.createElement('button');
                    cancel.type = 'button';
                    cancel.className = 'secondary-btn';
                    cancel.textContent = 'Cancel';
                    cancel.addEventListener('click', () => confirmation.remove());
                    yes.addEventListener('click', async () => {
                        yes.disabled = true;
                        cancel.disabled = true;
                        try {
                            await request(`/api/sharing/files/${encodeURIComponent(file.id)}`, {method:'DELETE'});
                            if (selectedFile && selectedFile.id === file.id) {
                                panel.hidden = true;
                                selectedFile = null;
                            }
                            await refresh();
                            status.textContent = `${file.name} deleted successfully.`;
                        } catch (e) {
                            setMessage(status, `Could not delete ${file.name}: ${e.message}`, true);
                            yes.disabled = false;
                            cancel.disabled = false;
                        }
                    });
                    confirmation.append(warning, yes, cancel);
                    cell.append(confirmation);
                });
                cell.append(' ', del);
            }
        }
    }
    document.addEventListener('nas:refresh-sharing', () => {
        refresh().catch(e =>
            setMessage(status, "Unable to load shared files: " + e.message, true));
    });
    try { await refresh(); } catch (e) { status.textContent = e.message; }
})();
