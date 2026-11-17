package com.nas.webserver.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "file_metadata", indexes = {
    @Index(name = "idx_file_metadata_path", columnList = "storage_path", unique = true),
    @Index(name = "idx_file_metadata_uploaded_at", columnList = "uploaded_at")
})
public class FileMetadata {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="storage_path", nullable=false, unique=true, length=512)
    private String storagePath;
    @Column(name="original_name", nullable=false, length=255)
    private String originalName;
    @Column(name="file_size", nullable=false)
    private long fileSize;
    @Column(name="content_type", length=150)
    private String contentType;
    @Column(name="uploaded_by", length=50)
    private String uploadedBy;
    @Column(name="uploaded_at", nullable=false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id")
    private User owner;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }

    public Long getId() { return id; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String v) { storagePath=v; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String v) { originalName=v; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long v) { fileSize=v; }
    public String getContentType() { return contentType; }
    public void setContentType(String v) { contentType=v; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String v) { uploadedBy=v; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime value) { uploadedAt = value; }
}
