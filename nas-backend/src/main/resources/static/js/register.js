const registerForm = document.getElementById('registerForm');
const registerMessage = document.getElementById('registerMessage');
const registerButton = document.getElementById('registerButton');

registerForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    registerMessage.textContent = '';
    registerMessage.className = 'auth-message';

    const username = document.getElementById('username').value.trim();
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value;
    const confirmPassword = document.getElementById('confirmPassword').value;

    if (password !== confirmPassword) {
        showMessage('Passwords do not match.', true);
        return;
    }

    registerButton.disabled = true;
    registerButton.textContent = 'Creating account...';

    try {
        const csrfResponse = await fetch('/api/csrf', {
            method: 'GET',
            credentials: 'same-origin'
        });
        if (!csrfResponse.ok) throw new Error('Unable to start a secure registration session.');
        const csrf = await csrfResponse.json();

        const response = await fetch('/api/users/register', {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/json',
                [csrf.headerName]: csrf.token
            },
            body: JSON.stringify({ username, email, password })
        });

        if (response.status === 201) {
            showMessage('Account created successfully. Redirecting to login...', false);
            registerForm.reset();
            setTimeout(() => window.location.href = '/login.html', 1200);
            return;
        }

        let detail = 'Registration failed. Please check your information and try again.';
        if (response.status === 409) detail = 'That username or email is already in use.';
        else if (response.status === 400) detail = 'Please check the username, email, and password requirements.';
        showMessage(detail, true);
    } catch (error) {
        showMessage(error.message || 'Something went wrong. Please try again.', true);
    } finally {
        registerButton.disabled = false;
        registerButton.textContent = 'Create Account';
    }
});

function showMessage(message, isError) {
    registerMessage.textContent = message;
    registerMessage.classList.toggle('error', isError);
    registerMessage.classList.toggle('success', !isError);
}
