
"use strict";

const list = document.getElementById('userList');
const message = document.getElementById('adminMessage');

let csrf;

async function request(url, options = {}) {
  if (!csrf) {
    const r = await fetch('/api/csrf');

    if (!r.ok) {
      throw Error('CSRF unavailable');
    }

    csrf = await r.json();
  }

  const r = await fetch(url, {
    credentials: 'same-origin',
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.method && options.method !== 'GET'
        ? { [csrf.headerName]: csrf.token }
        : {}),
      ...(options.headers || {})
    }
  });

  if (!r.ok) {
    const detail = await r.text();

    throw Error(
      r.status === 409
        ? 'Conflict: username/email already exists or the account owns files.'
        : r.status === 403
          ? 'Administrator access required.'
          : `Request failed (${r.status}). ${detail.slice(0, 100)}`
    );
  }

  return r.status === 204 ? null : r.json();
}

function note(t, error = false) {
  message.textContent = t;
  message.style.color = error ? '#b32636' : '#22633b';
}

function el(tag, txt, cls) {
  const x = document.createElement(tag);

  if (txt !== undefined) {
    x.textContent = txt;
  }

  if (cls) {
    x.className = cls;
  }

  return x;
}

function field(name, value, type = 'text') {
  const label = el('label', name);
  const input = el('input');

  input.type = type;
  input.value = value || '';

  label.append(input);

  return { label, input };
}

function button(text, fn, cls) {
  const b = el('button', text, cls);

  b.type = 'button';

  b.addEventListener('click', async () => {
    b.disabled = true;

    try {
      await fn();
    } catch (e) {
      note(e.message, true);
    } finally {
      b.disabled = false;
    }
  });

  return b;
}

async function load() {
  const users = await request('/api/users');

  list.replaceChildren();

  for (const u of users) {
    // User card
    const card = el('article', undefined, 'admin-user');

    // User header
    const header = el('div', undefined, 'admin-user-header');
    const title = el('strong', u.username);

    header.append(title, el('small', u.email));
    card.append(header);

    // Profile fields
    const fields = el('div', undefined, 'admin-fields');

    const username = field('Username', u.username);
    const email = field('Email', u.email, 'email');
    const password = field(
      'New password (optional)',
      '',
      'password'
    );

    password.input.placeholder = 'Leave blank to keep current';

    fields.append(
      username.label,
      email.label,
      password.label
    );

    card.append(fields);

    // Role selection
    const controls = el('div', undefined, 'admin-actions');
    const role = el('select');

    for (const name of ['USER', 'ADMIN']) {
      const option = el('option', name);
      option.value = name;
      role.append(option);
    }

    role.value = u.roleName || 'USER';

    const roleLabel = el('label', 'Role ');
    roleLabel.append(role);

    controls.append(roleLabel);

    // Permission toggles
    const perms = {};

    for (const [key, label] of [
      ['canRead', 'Read'],
      ['canWrite', 'Write'],
      ['canEdit', 'Edit']
    ]) {
      const b = el('button', label, 'permission-toggle');

      b.type = 'button';
      b.setAttribute('aria-pressed', String(!!u[key]));

      b.addEventListener('click', () => {
        b.setAttribute(
          'aria-pressed',
          String(b.getAttribute('aria-pressed') !== 'true')
        );
      });

      perms[key] = b;
      controls.append(b);
    }

    card.append(controls);

    // User actions
    const actions = el('div', undefined, 'admin-actions');

    // Save profile
    actions.append(
      button('Save profile', async () => {
        await request(`/api/users/${u.userId}`, {
          method: 'PUT',
          body: JSON.stringify({
            username: username.input.value.trim(),
            email: email.input.value.trim(),
            password: password.input.value
          })
        });

        note('Profile saved.');
        await load();
      })
    );

    // Save role
    actions.append(
      button('Save role', async () => {
        await request(`/api/users/${u.userId}/role`, {
          method: 'PUT',
          body: JSON.stringify({
            roleName: role.value
          })
        });

        note('Role updated.');
        await load();
      })
    );

    // Save permissions
    actions.append(
      button('Save permissions', async () => {
        await request(`/api/users/${u.userId}/permissions`, {
          method: 'PUT',
          body: JSON.stringify(
            Object.fromEntries(
              Object.entries(perms).map(([k, b]) => [
                k,
                b.getAttribute('aria-pressed') === 'true'
              ])
            )
          )
        });

        note('Permissions saved.');
        await load();
      })
    );

    // Delete user
    actions.append(
      button('Delete user', async () => {
        if (
          !window.confirm(
            `Delete account ${u.username}? This cannot be undone.`
          )
        ) {
          return;
        }

        await request(`/api/users/${u.userId}`, {
          method: 'DELETE'
        });

        note('User deleted.');
        await load();
      }, 'danger')
    );

    card.append(actions);
    list.append(card);
  }

  if (!users.length) {
    list.textContent = 'No accounts found.';
  }
}

// Create new user
document.getElementById('createUser').addEventListener(
  'submit',
  async e => {
    e.preventDefault();

    const form = e.currentTarget;
    const data = Object.fromEntries(new FormData(form));

    try {
      await request('/api/users/register', {
        method: 'POST',
        body: JSON.stringify(data)
      });

      form.reset();
      note('Account created.');

      await load();
    } catch (err) {
      note(err.message, true);
    }
  }
);

// Load users when the page initializes
load().catch(e => note(e.message, true));
