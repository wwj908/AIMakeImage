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
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  created_at DATETIME,
  UNIQUE KEY idx_users_username (username),
  UNIQUE KEY idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS artworks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  title VARCHAR(120) NOT NULL,
  tags VARCHAR(300),
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

CREATE TABLE IF NOT EXISTS chat_sessions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  client_id VARCHAR(80) NOT NULL,
  title VARCHAR(120) NOT NULL,
  pinned BIT NOT NULL DEFAULT 0,
  messages_json LONGTEXT NOT NULL,
  created_at DATETIME,
  updated_at DATETIME,
  UNIQUE KEY idx_chat_sessions_owner_client (owner_id, client_id),
  KEY idx_chat_sessions_owner_updated (owner_id, updated_at),
  CONSTRAINT fk_chat_sessions_owner FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS system_settings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  setting_key VARCHAR(80) NOT NULL,
  setting_value VARCHAR(2000),
  updated_at DATETIME,
  UNIQUE KEY idx_system_settings_key (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS openai_providers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  base_url VARCHAR(500) NOT NULL,
  api_key VARCHAR(1000) NOT NULL,
  model VARCHAR(120) NOT NULL,
  enabled BIT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 1,
  created_at DATETIME,
  updated_at DATETIME,
  KEY idx_openai_providers_enabled_sort (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
