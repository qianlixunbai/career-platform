-- One-time forward migration for the M7/P2-A learning-material file index.
-- Apply this script once after sql/006_create_application_tables.sql; do not
-- rerun it against a schema where these columns/key already exist.
USE career_platform;

ALTER TABLE learning_material
    ADD COLUMN file_content MEDIUMBLOB NULL AFTER description,
    ADD COLUMN file_name VARCHAR(255) NULL AFTER file_content,
    ADD COLUMN content_type VARCHAR(100) NULL AFTER file_name,
    ADD COLUMN file_size BIGINT NULL AFTER content_type,
    ADD COLUMN index_status VARCHAR(20) NOT NULL DEFAULT 'METADATA' AFTER file_size,
    ADD COLUMN chunk_count INT NULL AFTER index_status,
    ADD COLUMN embedding_identity VARCHAR(128) NULL AFTER chunk_count,
    ADD UNIQUE KEY uk_learning_material_id_user_id (id, user_id);

CREATE TABLE IF NOT EXISTS learning_material_chunk (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    text TEXT NOT NULL,
    location_label VARCHAR(255) NOT NULL,
    page_number INT NULL,
    embedding JSON NOT NULL,
    embedding_identity VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_material_chunk_material_index (material_id, chunk_index),
    KEY idx_learning_material_chunk_owner_material (user_id, material_id),
    KEY idx_learning_material_chunk_identity (user_id, embedding_identity),
    CONSTRAINT fk_learning_material_chunk_user FOREIGN KEY (user_id)
        REFERENCES app_user (id),
    CONSTRAINT fk_learning_material_chunk_material_owner FOREIGN KEY (material_id, user_id)
        REFERENCES learning_material (id, user_id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
