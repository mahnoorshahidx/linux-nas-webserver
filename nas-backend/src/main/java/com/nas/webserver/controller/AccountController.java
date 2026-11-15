package com.nas.webserver.controller;

import com.nas.webserver.dto.UserResponse;
import com.nas.webserver.model.User;
import com.nas.webserver.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    public AccountController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }
    public record ProfileRequest(@NotBlank @Size(min=3,max=50) String username,
        @NotBlank @Email @Size(max=100) String email) {}
    public record PasswordRequest(@NotBlank String currentPassword,
        @NotBlank @Size(min=8,max=72) String newPassword) {}

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication auth) {
        return users.findByUsername(auth.getName())
            .map(u -> ResponseEntity.ok(new UserResponse(u)))
            .orElse(ResponseEntity.notFound().build());
    }
    @PutMapping("/me")
    public ResponseEntity<?> update(@Valid @RequestBody ProfileRequest req,
            Authentication auth, HttpServletRequest http) {
        User u = users.findByUsername(auth.getName()).orElse(null);
        if (u == null) return ResponseEntity.notFound().build();
        String username = req.username().trim();
        String email = req.email().trim().toLowerCase();
        if (users.existsByUsernameAndUserIdNot(username,u.getUserId()) ||
            users.existsByEmailAndUserIdNot(email,u.getUserId()))
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username or email is already taken.");
        boolean changed = !username.equals(u.getUsername());
        u.setUsername(username); u.setEmail(email);
        User saved = users.save(u);
        // Force reauthentication if username changes; prevents stale principal in session.
        if (changed) http.getSession().invalidate();
        return ResponseEntity.ok(new UserResponse(saved));
    }
    @PutMapping("/me/password")
    public ResponseEntity<?> password(@Valid @RequestBody PasswordRequest req,
            Authentication auth, HttpServletRequest http) {
        User u = users.findByUsername(auth.getName()).orElse(null);
        if (u == null) return ResponseEntity.notFound().build();
        if (!encoder.matches(req.currentPassword(),u.getPassword()))
            return ResponseEntity.badRequest().body("Current password is incorrect.");
        if (encoder.matches(req.newPassword(),u.getPassword()))
            return ResponseEntity.badRequest().body("New password must be different.");
        u.setPassword(encoder.encode(req.newPassword())); users.save(u);
        http.getSession().invalidate();
        return ResponseEntity.noContent().build();
    }
}
