USE career_platform;

CREATE TABLE IF NOT EXISTS learning_plan (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    week_start DATE NOT NULL,
    week_end DATE NOT NULL,
    main_goal VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_plan_user_week_start (user_id, week_start),
    UNIQUE KEY uk_learning_plan_id_user_id (id, user_id),
    CONSTRAINT fk_learning_plan_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS learning_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    status VARCHAR(30) NOT NULL,
    planned_minutes INT NULL,
    due_date DATE NULL,
    sort_order INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_task_id_user_id (id, user_id),
    UNIQUE KEY uk_learning_task_id_plan_id_user_id (id, plan_id, user_id),
    KEY idx_learning_task_plan_user (plan_id, user_id),
    CONSTRAINT fk_learning_task_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_learning_task_plan_owner FOREIGN KEY (plan_id, user_id)
        REFERENCES learning_plan (id, user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS study_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    studied_at DATETIME NOT NULL,
    duration_minutes INT NOT NULL,
    content TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_study_record_task_user (task_id, user_id),
    CONSTRAINT fk_study_record_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_study_record_task_owner FOREIGN KEY (task_id, user_id)
        REFERENCES learning_task (id, user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS weekly_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    summary TEXT NULL,
    achievements TEXT NULL,
    problems TEXT NULL,
    next_steps TEXT NULL,
    reviewed_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_weekly_review_plan_id (plan_id),
    KEY idx_weekly_review_plan_user (plan_id, user_id),
    CONSTRAINT fk_weekly_review_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_weekly_review_plan_owner FOREIGN KEY (plan_id, user_id)
        REFERENCES learning_plan (id, user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS learning_note (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    task_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_learning_note_plan_user (plan_id, user_id),
    KEY idx_learning_note_task_plan_user (task_id, plan_id, user_id),
    CONSTRAINT fk_learning_note_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_learning_note_plan_owner FOREIGN KEY (plan_id, user_id)
        REFERENCES learning_plan (id, user_id),
    CONSTRAINT fk_learning_note_task_plan_owner FOREIGN KEY (task_id, plan_id, user_id)
        REFERENCES learning_task (id, plan_id, user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS learning_material (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    task_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    source_url VARCHAR(2048) NULL,
    description TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_learning_material_plan_user (plan_id, user_id),
    KEY idx_learning_material_task_plan_user (task_id, plan_id, user_id),
    CONSTRAINT fk_learning_material_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_learning_material_plan_owner FOREIGN KEY (plan_id, user_id)
        REFERENCES learning_plan (id, user_id),
    CONSTRAINT fk_learning_material_task_plan_owner FOREIGN KEY (task_id, plan_id, user_id)
        REFERENCES learning_task (id, plan_id, user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
