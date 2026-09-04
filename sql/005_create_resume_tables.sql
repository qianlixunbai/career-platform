USE career_platform;

CREATE TABLE IF NOT EXISTS resume (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_resume_id_user_id (id, user_id),
    KEY idx_resume_user_updated (user_id, updated_at),
    CONSTRAINT fk_resume_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resume_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    resume_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    label VARCHAR(200) NULL,
    status VARCHAR(30) NOT NULL,
    finalized_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_resume_version_resume_no (resume_id, version_no),
    UNIQUE KEY uk_resume_version_id_user_id (id, user_id),
    KEY idx_resume_version_resume_user_no (resume_id, user_id, version_no),
    CONSTRAINT fk_resume_version_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_resume_version_resume_owner FOREIGN KEY (resume_id, user_id)
        REFERENCES resume (id, user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resume_content_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    section_type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NULL,
    content TEXT NOT NULL,
    source_type VARCHAR(30) NULL,
    source_id BIGINT NULL,
    sort_order INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_resume_content_item_version_user_sort (version_id, user_id, sort_order, id),
    CONSTRAINT fk_resume_content_item_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_resume_content_item_version_owner FOREIGN KEY (version_id, user_id)
        REFERENCES resume_version (id, user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
