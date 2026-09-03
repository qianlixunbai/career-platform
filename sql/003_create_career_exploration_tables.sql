USE career_platform;

CREATE TABLE IF NOT EXISTS career_goal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    target_position VARCHAR(100) NOT NULL,
    target_city VARCHAR(100) NULL,
    target_industry VARCHAR(100) NULL,
    target_company_preference VARCHAR(255) NULL,
    salary_expectation VARCHAR(100) NULL,
    notes TEXT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_career_goal_user_status_updated (user_id, status, updated_at),
    CONSTRAINT fk_career_goal_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS company (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    industry VARCHAR(100) NULL,
    city VARCHAR(100) NULL,
    website VARCHAR(255) NULL,
    size VARCHAR(50) NULL,
    notes TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_company_id_user_id (id, user_id),
    KEY idx_company_user_name (user_id, name),
    CONSTRAINT fk_company_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    city VARCHAR(100) NULL,
    job_type VARCHAR(30) NOT NULL,
    publish_date DATE NULL,
    deadline DATE NULL,
    raw_jd TEXT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_name VARCHAR(150) NULL,
    source_url VARCHAR(500) NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_job_user_archived_updated (user_id, archived, updated_at),
    KEY idx_job_company_user (company_id, user_id),
    CONSTRAINT fk_job_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_job_company_owner FOREIGN KEY (company_id, user_id) REFERENCES company (id, user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS job_requirement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    requirement_type VARCHAR(20) NOT NULL,
    skill_id BIGINT NULL,
    requirement_text VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_job_requirement_job_created (job_id, created_at),
    KEY idx_job_requirement_skill_id (skill_id),
    CONSTRAINT fk_job_requirement_job FOREIGN KEY (job_id) REFERENCES job (id),
    CONSTRAINT fk_job_requirement_skill FOREIGN KEY (skill_id) REFERENCES skill (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS job_note (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_job_note_job_created (job_id, created_at),
    CONSTRAINT fk_job_note_job FOREIGN KEY (job_id) REFERENCES job (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
