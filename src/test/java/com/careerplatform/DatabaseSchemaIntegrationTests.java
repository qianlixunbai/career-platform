package com.careerplatform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DatabaseSchemaIntegrationTests {

    private static final List<String> MILESTONE_TWO_TABLES = List.of(
            "user_profile",
            "education_experience",
            "skill",
            "user_skill",
            "project_experience",
            "internship_experience",
            "certificate_award",
            "career_goal",
            "company",
            "job",
            "job_requirement",
            "job_note"
    );

    private static final List<String> REQUIRED_FOREIGN_KEYS = List.of(
            "fk_user_profile_user",
            "fk_education_experience_user",
            "fk_user_skill_user",
            "fk_user_skill_skill",
            "fk_project_experience_user",
            "fk_internship_experience_user",
            "fk_certificate_award_user",
            "fk_career_goal_user",
            "fk_company_user",
            "fk_job_user",
            "fk_job_company_owner",
            "fk_job_requirement_job",
            "fk_job_requirement_skill",
            "fk_job_note_job"
    );

    private static final List<String> REQUIRED_UNIQUE_INDEXES = List.of(
            "uk_user_profile_user_id",
            "uk_skill_name",
            "uk_user_skill_user_id_skill_id",
            "uk_company_id_user_id"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void milestoneTwoTablesAndCoreConstraintsShouldExistInRealMySql() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class
        );
        List<String> foreignKeys = jdbcTemplate.queryForList(
                "SELECT constraint_name FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = DATABASE()",
                String.class
        );
        List<String> uniqueIndexes = jdbcTemplate.queryForList(
                "SELECT DISTINCT index_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND non_unique = 0",
                String.class
        );

        assertThat(tables).containsAll(MILESTONE_TWO_TABLES);
        assertThat(foreignKeys).containsAll(REQUIRED_FOREIGN_KEYS);
        assertThat(uniqueIndexes).containsAll(REQUIRED_UNIQUE_INDEXES);
    }
}
