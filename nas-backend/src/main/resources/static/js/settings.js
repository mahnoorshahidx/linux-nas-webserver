const $ = id => document.getElementById(id);

async function token() {
    const r = await fetch('/api/csrf', { credentials: 'same-origin' });

    if (!r.ok)
        throw Error('Unable to get security token');
    return r.json()
}

async function api(path, method = 'GET', body) {
    const headers = {};

    if (method !== 'GET') {
        const t = await token();
        headers[t.headerName] = t.token;
        headers['Content-Type'] = 'application/json'
    }

    const r = await fetch(path, { method, headers, credentials: 'same-origin', body: body ? JSON.stringify(body) : undefined });
    if (!r.ok) {
        let detail = await r.text();
        throw Error(r.status === 403 ? 'Permission denied or session expired' : detail || `HTTP ${r.status}`)
    }

    if (r.status === 204)
        return null;

    return r.json()
}

function status(id, msg, err = false) {
    $(id).textContent = msg;
    $(id).style.color = err ? '#b73545' : '#34815a'
}

let selfId = null;
let dirty = false;

async function account() {
    try {
        const a = await api('/api/account/me');
        selfId = a.userId; $('profile').elements.username.value = a.username;
        $('profile').elements.email.value = a.email;
        $('accountInfo').textContent = `Role: ${a.roleName}`;

        if (a.roleName === 'ADMIN') {
            $('adminSection').hidden = false;
            await users()
        }
    }

    catch (e) {
        status('profileStatus', e.message, true)
    }
}

$('profile').addEventListener('input', () => dirty = true);
$('profile').addEventListener('submit', async e => {
    e.preventDefault(); const f = e.target;
    const oldName = $('accountInfo').textContent;

    try {
        const a = await api('/api/account/me', 'PUT', {
            username: f.elements.username.value.trim(),
            email: f.elements.email.value.trim()
        });

        dirty = false;
        status('profileStatus', 'Saved. If you changed your username, please sign in again.');

        if (oldName && a.username !== undefined)
            $('accountInfo').textContent = `Role: ${a.roleName}`;
    }

    catch (err) {
        status('profileStatus', err.message, true)
    }
});

$('password').addEventListener('submit', async e => {
    e.preventDefault();
    const f = e.target;

    if (f.elements.newPassword.value !== f.elements.confirmPassword.value) {
        status('passwordStatus', 'Passwords do not match.', true);
        return
    }

    try {
        await api('/api/account/me/password', 'PUT', {
            currentPassword: f.elements.currentPassword.value,
            newPassword: f.elements.newPassword.value
        });

        f.reset();
        status('passwordStatus', 'Password updated. Please sign in again.')
    }

    catch (err) {
        status('passwordStatus', err.message, true)
    }
});

function size(n) {
    let i = 0;
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];

    while (n >= 1024 && i < 4) {
        n /= 1024; i++
    }

    return n.toFixed(i ? 1 : 0) + ' ' + units[i]
}

async function storage() {
    try {
        const s = await api('/api/storage/overview');
        $('files').textContent = s.fileCount;
        $('folders').textContent = s.folderCount;
        $('used').textContent = size(s.usedBytes);
        $('capacity').textContent = size(s.totalBytes);
        $('storageStatus').textContent = ''
    }

    catch (e) {
        $('storageStatus').textContent = e.message
    }
}

async function users() {
    try {
        const all = await api('/api/users');
        const tbody = $('users');
        tbody.replaceChildren();

        for (const u of all) {
            const tr = document.createElement('tr');
            for (const v of [u.username, u.email, u.roleName]) {
                const td = document.createElement('td');
                td.textContent = v; tr.appendChild(td)
            }

            const actions = document.createElement('td');
            if (u.userId === selfId) {
                actions.textContent = 'Your account'
            }

            else {
                const select = document.createElement('select');

                for (const role of ['USER', 'ADMIN']) {
                    const op = document.createElement('option');
                    op.value = role;
                    op.textContent = role;
                    select.appendChild(op)
                }

                select.value = u.roleName;
                select.addEventListener('change', async () => {
                    try {
                        await api(`/api/users/${u.userId}/role`, 'PUT', { roleName: select.value });
                        status('adminStatus', `Role updated for ${u.username}`)
                    }

                    catch (e) {
                        status('adminStatus', e.message, true);
                        select.value = u.roleName
                    }
                });

                actions.appendChild(select);
                const del = document.createElement('button');
                del.type = 'button';
                del.className = 'secondary-btn';
                del.textContent = 'Delete';
                del.addEventListener('click', async () => {
                    if (!confirm(`Delete account ${u.username}? This cannot be undone.`)) return;
                    try {
                        await api(`/api/users/${u.userId}`, 'DELETE');
                        await users(); status('adminStatus', 'Account deleted')
                    }
                    catch (e) {
                        status('adminStatus', e.message, true)
                    }
                });

                actions.appendChild(del)
            }
            tr.appendChild(actions);
            tbody.appendChild(tr)
        }
    }

    catch (e) {
        status('adminStatus', e.message, true)
    }
}

$('logout').addEventListener('click', async () => {
    try {
        const t = await token();
        const form = document.createElement('form');
        form.method = 'POST'; form.action = '/logout';
        const i = document.createElement('input');
        i.type = 'hidden';
        i.name = t.parameterName;
        i.value = t.token;
        form.appendChild(i);
        document.body.appendChild(form);
        form.submit()
    }

    catch (e) {
        alert(e.message)
    }
});

$('refreshStorage').addEventListener('click', storage);
$('compact').checked = localStorage.getItem('nas-compact') === 'true';
$('confirm').checked = localStorage.getItem('nas-confirm') === 'true';

function applyCompact() {
    document.body.classList.toggle('nas-compact', $('compact').checked)
}

applyCompact();
$('compact').addEventListener('change', () => {
    localStorage.setItem('nas-compact', $('compact').checked); applyCompact()
});
$('confirm').addEventListener('change', () => localStorage.setItem('nas-confirm', $('confirm').checked));
window.addEventListener('beforeunload', e => { if (dirty && $('confirm').checked) { e.preventDefault(); e.returnValue = '' } });
account();
storage();
