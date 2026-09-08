USE career_platform;

CREATE TABLE IF NOT EXISTS resume_file (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    resume_version_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    file_data MEDIUMBLOB NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_resume_file_resume_version_id (resume_version_id),
    KEY idx_resume_file_user_version (user_id, resume_version_id),
    KEY idx_resume_file_version_user (resume_version_id, user_id),
    CONSTRAINT fk_resume_file_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_resume_file_version_owner FOREIGN KEY (resume_version_id, user_id)
        REFERENCES resume_version (id, user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
