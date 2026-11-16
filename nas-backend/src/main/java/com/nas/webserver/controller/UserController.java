package com.nas.webserver.controller;

import com.nas.webserver.dto.UserResponse;
import com.nas.webserver.model.Role;
import com.nas.webserver.model.User;
import com.nas.webserver.repository.RoleRepository;
import com.nas.webserver.repository.UserRepository;
import com.nas.webserver.repository.FileMetadataRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final FileMetadataRepository fileMetadata;

    public UserController(UserRepository users, RoleRepository roles,
                          PasswordEncoder encoder, FileMetadataRepository fileMetadata) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.fileMetadata = fileMetadata;
    }

    public record RegistrationRequest(
        @NotBlank @Size(min=3, max=50) String username,
        @NotBlank @Email @Size(max=100) String email,
        @NotBlank @Size(min=8, max=72) String password) {}

    public record UpdateUserRequest(
        @NotBlank @Size(min=3, max=50) String username,
        @NotBlank @Email @Size(max=100) String email,
        String password) {}

    public record RoleRequest(@NotBlank String roleName) {}
    public record PermissionsRequest(boolean canRead, boolean canWrite, boolean canEdit) {}

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegistrationRequest request) {
        if (users.existsByUsername(request.username().trim())
                || users.existsByEmail(request.email().trim().toLowerCase())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        Role role = roles.findByRoleName("USER").orElseGet(() -> {
            Role created = new Role();
            created.setRoleName("USER");
            return roles.save(created);
        });
        User user = new User();
        user.setUsername(request.username().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPassword(encoder.encode(request.password()));
        user.setRole(role);
        // New accounts can use their own NAS storage immediately.
        // Administrators may restrict these account-level permissions later.
        user.setCanRead(true);
        user.setCanWrite(true);
        user.setCanEdit(true);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UserResponse(users.save(user)));
    }

    @GetMapping
    public List<UserResponse> getAllUsers() {
        return users.findAll().stream().map(UserResponse::new).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        return users.findById(id).map(u -> ResponseEntity.ok(new UserResponse(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(
            @PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        if ((users.existsByUsernameAndUserIdNot(request.username(), id))
                || users.existsByEmailAndUserIdNot(request.email().toLowerCase(), id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        return users.findById(id).map(user -> {
            user.setUsername(request.username().trim());
            user.setEmail(request.email().trim().toLowerCase());
            if (request.password() != null && !request.password().isBlank()) {
                if (request.password().length() < 8 || request.password().length() > 72)
                    throw new IllegalArgumentException("Password must be 8-72 characters");
                user.setPassword(encoder.encode(request.password()));
            }
            return ResponseEntity.ok(new UserResponse(users.save(user)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<UserResponse> changeRole(
            @PathVariable Long id, @Valid @RequestBody RoleRequest request,
            org.springframework.security.core.Authentication authentication) {
        if (users.findById(id).map(u -> u.getUsername().equals(authentication.getName())).orElse(false))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String roleName = request.roleName().trim().toUpperCase();
        if (!roleName.equals("USER") && !roleName.equals("ADMIN"))
            return ResponseEntity.badRequest().build();
        Role role = roles.findByRoleName(roleName).orElseGet(() -> {
            Role created = new Role();
            created.setRoleName(roleName);
            return roles.save(created);
        });
        return users.findById(id).map(user -> {
            user.setRole(role);
            return ResponseEntity.ok(new UserResponse(users.save(user)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/permissions")
    public ResponseEntity<UserResponse> changePermissions(
            @PathVariable Long id, @RequestBody PermissionsRequest request) {
        return users.findById(id).map(user -> {
            user.setCanRead(request.canRead());
            user.setCanWrite(request.canWrite());
            user.setCanEdit(request.canEdit());
            return ResponseEntity.ok(new UserResponse(users.save(user)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
            org.springframework.security.core.Authentication authentication) {
        if (users.findById(id).map(u -> u.getUsername().equals(authentication.getName())).orElse(false))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (!users.existsById(id)) return ResponseEntity.notFound().build();
        if (fileMetadata.findAll().stream().anyMatch(f -> f.getOwner() != null && id.equals(f.getOwner().getUserId())))
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        users.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
