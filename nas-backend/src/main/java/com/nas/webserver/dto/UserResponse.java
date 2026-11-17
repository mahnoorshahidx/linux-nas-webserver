package com.nas.webserver.dto;

import com.nas.webserver.model.User;
import java.time.LocalDateTime;

public class UserResponse {

    private Long userId;
    private String username;
    private String email;
    private LocalDateTime createdAt;
    private String roleName;
    private boolean canRead;
    private boolean canWrite;
    private boolean canEdit;

    public UserResponse(User user) {
        this.userId = user.getUserId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.canRead = user.isCanRead();
        this.canWrite = user.isCanWrite();
        this.canEdit = user.isCanEdit();
        this.createdAt = user.getCreatedAt();

        if (user.getRole() != null) {
            this.roleName = user.getRole().getRoleName();
        }
    }

    public boolean isCanRead() { return canRead; }
    public boolean isCanWrite() { return canWrite; }
    public boolean isCanEdit() { return canEdit; }
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
