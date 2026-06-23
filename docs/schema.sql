CREATE DATABASE IF NOT EXISTS make_image
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE make_image;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(40) NOT NULL,
  email VARCHAR(120) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  avatar_url VARCHAR(300),
  created_at DATETIME,
  UNIQUE KEY idx_users_username (username),
  UNIQUE KEY idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS artworks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  title VARCHAR(120) NOT NULL,
  prompt VARCHAR(1200) NOT NULL,
  negative_prompt VARCHAR(1200),
  mode VARCHAR(24) NOT NULL,
  image_url VARCHAR(500) NOT NULL,
  source_image_url VARCHAR(500),
  public_work BIT NOT NULL DEFAULT 0,
  download_count BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME,
  updated_at DATETIME,
  KEY idx_artworks_owner (owner_id),
  KEY idx_artworks_public_created (public_work, created_at),
  CONSTRAINT fk_artworks_owner FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
