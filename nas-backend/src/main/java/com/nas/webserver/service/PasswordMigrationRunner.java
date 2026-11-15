package com.nas.webserver.service;

import com.nas.webserver.model.User;
import com.nas.webserver.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordMigrationRunner implements CommandLineRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    public PasswordMigrationRunner(UserRepository users, PasswordEncoder encoder) {
        this.users = users; this.encoder = encoder;
    }
    @Override
    public void run(String... args) {
        for (User user : users.findAll()) {
            String password = user.getPassword();
            // BCrypt hashes begin with "$2a$", "$2b$", or "$2y$".
            if (password != null && !password.matches("^\\$2[aby]\\$.*")) {
                user.setPassword(encoder.encode(password));
                users.save(user);
            }
        }
    }
}
