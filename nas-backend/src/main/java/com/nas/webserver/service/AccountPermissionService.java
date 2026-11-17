package com.nas.webserver.service;

import com.nas.webserver.model.User;
import com.nas.webserver.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("accountPermissionService")
public class AccountPermissionService {
    private final UserRepository users;
    public AccountPermissionService(UserRepository users) { this.users = users; }
    private User current(Authentication auth) {
        if (auth == null) return null;
        return users.findByUsername(auth.getName()).orElse(null);
    }
    private boolean admin(User user) {
        return user != null && user.getRole() != null && "ADMIN".equals(user.getRole().getRoleName());
    }
    public boolean canRead(Authentication auth) { User u = current(auth); return admin(u) || (u != null && u.isCanRead()); }
    public boolean canWrite(Authentication auth) { User u = current(auth); return admin(u) || (u != null && u.isCanWrite()); }
    public boolean canEdit(Authentication auth) { User u = current(auth); return admin(u) || (u != null && u.isCanEdit()); }
}
