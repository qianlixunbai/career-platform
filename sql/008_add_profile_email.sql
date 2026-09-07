USE career_platform;

ALTER TABLE user_profile
    ADD COLUMN email VARCHAR(254) NULL AFTER phone;