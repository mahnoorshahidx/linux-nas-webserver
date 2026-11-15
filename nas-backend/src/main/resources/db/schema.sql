-- Run as a MySQL administrator. Replace the placeholder password.
CREATE DATABASE IF NOT EXISTS nas_database
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'nas_user'@'localhost' IDENTIFIED BY 'PLACEHOLDER_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON nas_database.* TO 'nas_user'@'localhost';
FLUSH PRIVILEGES;

USE nas_database;
CREATE TABLE IF NOT EXISTS roles (
  role_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  role_name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS users (
  user_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  email VARCHAR(100) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  role_id BIGINT,
  CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(role_id)
);

CREATE TABLE IF NOT EXISTS file_metadata (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  storage_path VARCHAR(512) NOT NULL UNIQUE,
  original_name VARCHAR(255) NOT NULL,
  file_size BIGINT NOT NULL,
  content_type VARCHAR(150),
  uploaded_by VARCHAR(50),
  uploaded_at DATETIME(6) NOT NULL,
  INDEX idx_file_metadata_uploaded_at (uploaded_at)
);

INSERT IGNORE INTO roles(role_name) VALUES ('USER'), ('ADMIN');
