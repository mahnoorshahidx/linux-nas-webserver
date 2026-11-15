package com.nas.webserver.service;

import com.nas.webserver.model.Role;
import com.nas.webserver.model.User;
import com.nas.webserver.repository.RoleRepository;
import com.nas.webserver.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements CommandLineRunner {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    @Value("${nas.admin.username:}") private String username;
    @Value("${nas.admin.email:}") private String email;
    @Value("${nas.admin.password:}") private String password;

    public AdminBootstrapRunner(UserRepository users, RoleRepository roles,
                                PasswordEncoder encoder) {
        this.users=users; this.roles=roles; this.encoder=encoder;
    }

    @Override
    public void run(String... args) {
        Role admin = roles.findByRoleName("ADMIN").orElseGet(() -> {
            Role role = new Role(); role.setRoleName("ADMIN");
            return roles.save(role);
        });
        roles.findByRoleName("USER").orElseGet(() -> {
            Role role = new Role(); role.setRoleName("USER");
            return roles.save(role);
        });
        // Optional first-admin bootstrap. Credentials are supplied only through environment variables.
        if (!username.isBlank() && !email.isBlank() && !password.isBlank()
                && users.findByUsername(username).isEmpty()) {
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPassword(encoder.encode(password));
            user.setRole(admin);
            users.save(user);
        }
    }
}
