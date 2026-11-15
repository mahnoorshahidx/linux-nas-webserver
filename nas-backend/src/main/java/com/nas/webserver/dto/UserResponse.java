package com.nas.webserver.dto;

import com.nas.webserver.model.User;
import java.time.LocalDateTime;

public class UserResponse {

    private Long userId;
    private String username;
    private String email;
    private LocalDateTime createdAt;
    private String roleName;

    public UserResponse(User user) {
        this.userId = user.getUserId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.createdAt = user.getCreatedAt();

        if (user.getRole() != null) {
            this.roleName = user.getRole().getRoleName();
        }
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getRoleName() {
        return roleName;
    }
}
