USE career_platform;

CREATE TABLE IF NOT EXISTS application (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    resume_version_id BIGINT NOT NULL,
    current_stage VARCHAR(30) NOT NULL,
    end_reason VARCHAR(50) NULL,
    end_note VARCHAR(1000) NULL,
    job_title_snapshot VARCHAR(150) NOT NULL,
    company_name_snapshot VARCHAR(150) NOT NULL,
    location_snapshot VARCHAR(100) NULL,
    job_description_snapshot TEXT NULL,
    applied_at DATETIME NOT NULL,
    ended_at DATETIME NULL,
    ongoing_job_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN current_stage <> 'ENDED' THEN job_id ELSE NULL END
    ) STORED,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_application_id_user_id (id, user_id),
    UNIQUE KEY uk_application_user_ongoing_job_id (user_id, ongoing_job_id),
    KEY idx_application_user_stage_updated (user_id, current_stage, updated_at),
    KEY idx_application_job_user (job_id, user_id),
    CONSTRAINT fk_application_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_application_job FOREIGN KEY (job_id) REFERENCES job (id),
    CONSTRAINT fk_application_resume_version_owner FOREIGN KEY (resume_version_id, user_id)
        REFERENCES resume_version (id, user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS application_stage_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    from_stage VARCHAR(30) NULL,
    to_stage VARCHAR(30) NOT NULL,
    end_reason VARCHAR(50) NULL,
    note VARCHAR(1000) NULL,
    changed_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_application_stage_history_application_user_changed_id
        (application_id, user_id, changed_at, id),
    CONSTRAINT fk_application_stage_history_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_application_stage_history_application_owner FOREIGN KEY (application_id, user_id)
        REFERENCES application (id, user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS assessment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    scheduled_at DATETIME NULL,
    occurred_at DATETIME NULL,
    result VARCHAR(30) NULL,
    notes TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_assessment_application_user_scheduled_id (application_id, user_id, scheduled_at, id),
    CONSTRAINT fk_assessment_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_assessment_application_owner FOREIGN KEY (application_id, user_id)
        REFERENCES application (id, user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS interview (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    scheduled_at DATETIME NULL,
    occurred_at DATETIME NULL,
    result VARCHAR(30) NULL,
    notes TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_interview_application_user_round_id (application_id, user_id, round_no, id),
    CONSTRAINT fk_interview_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_interview_application_owner FOREIGN KEY (application_id, user_id)
        REFERENCES application (id, user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS offer (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    position_title VARCHAR(150) NULL,
    compensation VARCHAR(255) NULL,
    expires_at DATETIME NULL,
    notes TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_offer_application_id (application_id),
    KEY idx_offer_application_user (application_id, user_id),
    CONSTRAINT fk_offer_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_offer_application_owner FOREIGN KEY (application_id, user_id)
        REFERENCES application (id, user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS final_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    summary TEXT NOT NULL,
    lessons_learned TEXT NULL,
    improvements TEXT NULL,
    rating INT NULL,
    reviewed_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_final_review_application_id (application_id),
    KEY idx_final_review_application_user (application_id, user_id),
    CONSTRAINT fk_final_review_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_final_review_application_owner FOREIGN KEY (application_id, user_id)
        REFERENCES application (id, user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
