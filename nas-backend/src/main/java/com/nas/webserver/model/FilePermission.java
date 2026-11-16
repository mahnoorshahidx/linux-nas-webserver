package com.nas.webserver.model;

import jakarta.persistence.*;

@Entity
@Table(name = "file_permissions", uniqueConstraints = @UniqueConstraint(name = "unique_file_user", columnNames = {"file_id", "user_id"}))
public class FilePermission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    private Long permissionId;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private FileMetadata file;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "can_read", nullable = false)
    private boolean canRead;
    @Column(name = "can_write", nullable = false)
    private boolean canWrite;
    @Column(name = "can_edit", nullable = false)
    private boolean canEdit;
    public Long getPermissionId() { return permissionId; }
    public FileMetadata getFile() { return file; }
    public void setFile(FileMetadata value) { file = value; }
    public User getUser() { return user; }
    public void setUser(User value) { user = value; }
    public boolean isCanRead() { return canRead; }
    public void setCanRead(boolean value) { canRead = value; }
    public boolean isCanWrite() { return canWrite; }
    public void setCanWrite(boolean value) { canWrite = value; }
    public boolean isCanEdit() { return canEdit; }
    public void setCanEdit(boolean value) { canEdit = value; }
}
