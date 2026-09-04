package com.careerplatform;

import com.careerplatform.learning.mapper.LearningMaterialMapper;
import com.careerplatform.learning.mapper.LearningNoteMapper;
import com.careerplatform.learning.mapper.LearningPlanMapper;
import com.careerplatform.learning.mapper.LearningTaskMapper;
import com.careerplatform.learning.mapper.StudyRecordMapper;
import com.careerplatform.learning.mapper.WeeklyReviewMapper;
import com.careerplatform.resume.mapper.ResumeContentItemMapper;
import com.careerplatform.resume.mapper.ResumeMapper;
import com.careerplatform.resume.mapper.ResumeVersionMapper;
import com.careerplatform.application.mapper.ApplicationMapper;
import com.careerplatform.application.mapper.ApplicationStageHistoryMapper;
import com.careerplatform.application.mapper.AssessmentMapper;
import com.careerplatform.application.mapper.FinalReviewMapper;
import com.careerplatform.application.mapper.InterviewMapper;
import com.careerplatform.application.mapper.OfferMapper;
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

    private static final List<String> MILESTONE_THREE_TABLES = List.of(
            "learning_plan",
            "learning_task",
            "study_record",
            "weekly_review",
            "learning_note",
            "learning_material"
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

    private static final List<String> REQUIRED_LEARNING_FOREIGN_KEYS = List.of(
            "fk_learning_plan_user",
            "fk_learning_task_user",
            "fk_learning_task_plan_owner",
            "fk_study_record_user",
            "fk_study_record_task_owner",
            "fk_weekly_review_user",
            "fk_weekly_review_plan_owner",
            "fk_learning_note_user",
            "fk_learning_note_plan_owner",
            "fk_learning_note_task_plan_owner",
            "fk_learning_material_user",
            "fk_learning_material_plan_owner",
            "fk_learning_material_task_plan_owner"
    );

    private static final List<String> REQUIRED_UNIQUE_INDEXES = List.of(
            "uk_user_profile_user_id",
            "uk_skill_name",
            "uk_user_skill_user_id_skill_id",
            "uk_company_id_user_id"
    );

    private static final List<String> REQUIRED_LEARNING_UNIQUE_INDEXES = List.of(
            "uk_learning_plan_user_week_start",
            "uk_weekly_review_plan_id"
    );

    private static final List<String> MILESTONE_FOUR_RESUME_TABLES = List.of(
            "resume",
            "resume_version",
            "resume_content_item"
    );

    private static final List<String> REQUIRED_RESUME_FOREIGN_KEYS = List.of(
            "fk_resume_user",
            "fk_resume_version_user",
            "fk_resume_version_resume_owner",
            "fk_resume_content_item_user",
            "fk_resume_content_item_version_owner"
    );

    private static final List<ResumeForeignKeyColumn> EXPECTED_RESUME_FOREIGN_KEY_COLUMNS = List.of(
            new ResumeForeignKeyColumn("resume", "fk_resume_user", 1,
                    "user_id", "app_user", "id"),
            new ResumeForeignKeyColumn("resume_content_item", "fk_resume_content_item_user", 1,
                    "user_id", "app_user", "id"),
            new ResumeForeignKeyColumn("resume_content_item", "fk_resume_content_item_version_owner", 1,
                    "version_id", "resume_version", "id"),
            new ResumeForeignKeyColumn("resume_content_item", "fk_resume_content_item_version_owner", 2,
                    "user_id", "resume_version", "user_id"),
            new ResumeForeignKeyColumn("resume_version", "fk_resume_version_resume_owner", 1,
                    "resume_id", "resume", "id"),
            new ResumeForeignKeyColumn("resume_version", "fk_resume_version_resume_owner", 2,
                    "user_id", "resume", "user_id"),
            new ResumeForeignKeyColumn("resume_version", "fk_resume_version_user", 1,
                    "user_id", "app_user", "id")
    );

    private static final List<ResumeUniqueIndexColumn> EXPECTED_RESUME_UNIQUE_INDEX_COLUMNS = List.of(
            new ResumeUniqueIndexColumn("resume_version", "uk_resume_version_resume_no", 1, "resume_id"),
            new ResumeUniqueIndexColumn("resume_version", "uk_resume_version_resume_no", 2, "version_no")
    );

    private static final List<ResumeTableMetadata> EXPECTED_RESUME_TABLE_METADATA = List.of(
            new ResumeTableMetadata("resume", "InnoDB", "utf8mb4_unicode_ci"),
            new ResumeTableMetadata("resume_content_item", "InnoDB", "utf8mb4_unicode_ci"),
            new ResumeTableMetadata("resume_version", "InnoDB", "utf8mb4_unicode_ci")
    );

    private static final List<LearningForeignKeyColumn> EXPECTED_LEARNING_FOREIGN_KEY_COLUMNS = List.of(
            new LearningForeignKeyColumn("learning_material", "fk_learning_material_plan_owner", 1,
                    "plan_id", "learning_plan", "id"),
            new LearningForeignKeyColumn("learning_material", "fk_learning_material_plan_owner", 2,
                    "user_id", "learning_plan", "user_id"),
            new LearningForeignKeyColumn("learning_material", "fk_learning_material_task_plan_owner", 1,
                    "task_id", "learning_task", "id"),
            new LearningForeignKeyColumn("learning_material", "fk_learning_material_task_plan_owner", 2,
                    "plan_id", "learning_task", "plan_id"),
            new LearningForeignKeyColumn("learning_material", "fk_learning_material_task_plan_owner", 3,
                    "user_id", "learning_task", "user_id"),
            new LearningForeignKeyColumn("learning_material", "fk_learning_material_user", 1,
                    "user_id", "app_user", "id"),
            new LearningForeignKeyColumn("learning_note", "fk_learning_note_plan_owner", 1,
                    "plan_id", "learning_plan", "id"),
            new LearningForeignKeyColumn("learning_note", "fk_learning_note_plan_owner", 2,
                    "user_id", "learning_plan", "user_id"),
            new LearningForeignKeyColumn("learning_note", "fk_learning_note_task_plan_owner", 1,
                    "task_id", "learning_task", "id"),
            new LearningForeignKeyColumn("learning_note", "fk_learning_note_task_plan_owner", 2,
                    "plan_id", "learning_task", "plan_id"),
            new LearningForeignKeyColumn("learning_note", "fk_learning_note_task_plan_owner", 3,
                    "user_id", "learning_task", "user_id"),
            new LearningForeignKeyColumn("learning_note", "fk_learning_note_user", 1,
                    "user_id", "app_user", "id"),
            new LearningForeignKeyColumn("learning_plan", "fk_learning_plan_user", 1,
                    "user_id", "app_user", "id"),
            new LearningForeignKeyColumn("learning_task", "fk_learning_task_plan_owner", 1,
                    "plan_id", "learning_plan", "id"),
            new LearningForeignKeyColumn("learning_task", "fk_learning_task_plan_owner", 2,
                    "user_id", "learning_plan", "user_id"),
            new LearningForeignKeyColumn("learning_task", "fk_learning_task_user", 1,
                    "user_id", "app_user", "id"),
            new LearningForeignKeyColumn("study_record", "fk_study_record_task_owner", 1,
                    "task_id", "learning_task", "id"),
            new LearningForeignKeyColumn("study_record", "fk_study_record_task_owner", 2,
                    "user_id", "learning_task", "user_id"),
            new LearningForeignKeyColumn("study_record", "fk_study_record_user", 1,
                    "user_id", "app_user", "id"),
            new LearningForeignKeyColumn("weekly_review", "fk_weekly_review_plan_owner", 1,
                    "plan_id", "learning_plan", "id"),
            new LearningForeignKeyColumn("weekly_review", "fk_weekly_review_plan_owner", 2,
                    "user_id", "learning_plan", "user_id"),
            new LearningForeignKeyColumn("weekly_review", "fk_weekly_review_user", 1,
                    "user_id", "app_user", "id")
    );

    private static final List<LearningUniqueIndexColumn> EXPECTED_LEARNING_UNIQUE_INDEX_COLUMNS = List.of(
            new LearningUniqueIndexColumn("learning_plan", "uk_learning_plan_id_user_id", 1, "id"),
            new LearningUniqueIndexColumn("learning_plan", "uk_learning_plan_id_user_id", 2, "user_id"),
            new LearningUniqueIndexColumn("learning_plan", "uk_learning_plan_user_week_start", 1, "user_id"),
            new LearningUniqueIndexColumn("learning_plan", "uk_learning_plan_user_week_start", 2, "week_start"),
            new LearningUniqueIndexColumn("learning_task", "uk_learning_task_id_plan_id_user_id", 1, "id"),
            new LearningUniqueIndexColumn("learning_task", "uk_learning_task_id_plan_id_user_id", 2, "plan_id"),
            new LearningUniqueIndexColumn("learning_task", "uk_learning_task_id_plan_id_user_id", 3, "user_id"),
            new LearningUniqueIndexColumn("learning_task", "uk_learning_task_id_user_id", 1, "id"),
            new LearningUniqueIndexColumn("learning_task", "uk_learning_task_id_user_id", 2, "user_id"),
            new LearningUniqueIndexColumn("weekly_review", "uk_weekly_review_plan_id", 1, "plan_id")
    );

    private static final List<LearningTableMetadata> EXPECTED_LEARNING_TABLE_METADATA = List.of(
            new LearningTableMetadata("learning_material", "InnoDB", "utf8mb4_unicode_ci"),
            new LearningTableMetadata("learning_note", "InnoDB", "utf8mb4_unicode_ci"),
            new LearningTableMetadata("learning_plan", "InnoDB", "utf8mb4_unicode_ci"),
            new LearningTableMetadata("learning_task", "InnoDB", "utf8mb4_unicode_ci"),
            new LearningTableMetadata("study_record", "InnoDB", "utf8mb4_unicode_ci"),
            new LearningTableMetadata("weekly_review", "InnoDB", "utf8mb4_unicode_ci")
    );

    // MySQL reports omitted ON DELETE (the default RESTRICT behavior) as NO ACTION.
    private static final List<LearningForeignKeyRule> EXPECTED_LEARNING_FOREIGN_KEY_RULES = List.of(
            new LearningForeignKeyRule("learning_material", "fk_learning_material_plan_owner", "NO ACTION"),
            new LearningForeignKeyRule("learning_material", "fk_learning_material_task_plan_owner", "NO ACTION"),
            new LearningForeignKeyRule("learning_material", "fk_learning_material_user", "NO ACTION"),
            new LearningForeignKeyRule("learning_note", "fk_learning_note_plan_owner", "NO ACTION"),
            new LearningForeignKeyRule("learning_note", "fk_learning_note_task_plan_owner", "NO ACTION"),
            new LearningForeignKeyRule("learning_note", "fk_learning_note_user", "NO ACTION"),
            new LearningForeignKeyRule("learning_plan", "fk_learning_plan_user", "NO ACTION"),
            new LearningForeignKeyRule("learning_task", "fk_learning_task_plan_owner", "NO ACTION"),
            new LearningForeignKeyRule("learning_task", "fk_learning_task_user", "NO ACTION"),
            new LearningForeignKeyRule("study_record", "fk_study_record_task_owner", "NO ACTION"),
            new LearningForeignKeyRule("study_record", "fk_study_record_user", "NO ACTION"),
            new LearningForeignKeyRule("weekly_review", "fk_weekly_review_plan_owner", "NO ACTION"),
            new LearningForeignKeyRule("weekly_review", "fk_weekly_review_user", "NO ACTION")
    );

    private static final LearningMaterialSourceUrlMetadata EXPECTED_LEARNING_MATERIAL_SOURCE_URL =
            new LearningMaterialSourceUrlMetadata("varchar", 2048L, "YES");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LearningPlanMapper learningPlanMapper;

    @Autowired
    private LearningTaskMapper learningTaskMapper;

    @Autowired
    private StudyRecordMapper studyRecordMapper;

    @Autowired
    private WeeklyReviewMapper weeklyReviewMapper;

    @Autowired
    private LearningNoteMapper learningNoteMapper;

    @Autowired
    private LearningMaterialMapper learningMaterialMapper;

    @Autowired
    private ResumeMapper resumeMapper;

    @Autowired
    private ResumeVersionMapper resumeVersionMapper;

    @Autowired
    private ResumeContentItemMapper resumeContentItemMapper;

    @Autowired private ApplicationMapper applicationMapper;
    @Autowired private ApplicationStageHistoryMapper applicationStageHistoryMapper;
    @Autowired private AssessmentMapper assessmentMapper;
    @Autowired private InterviewMapper interviewMapper;
    @Autowired private OfferMapper offerMapper;
    @Autowired private FinalReviewMapper finalReviewMapper;

    @Test
    void milestoneFiveBApplicationSchemaAndMappersShouldExistInRealMySql() {
        List<String> applicationTables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
                        + "AND table_name IN ('application','application_stage_history','assessment','interview','offer','final_review')",
                String.class);
        assertThat(applicationTables).containsExactlyInAnyOrder(
                "application", "application_stage_history", "assessment", "interview", "offer", "final_review");

        Integer businessTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'",
                Integer.class);
        assertThat(businessTableCount).isEqualTo(28);

        List<String> foreignKeys = jdbcTemplate.queryForList(
                "SELECT constraint_name FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = DATABASE() AND table_name IN "
                        + "('application','application_stage_history','assessment','interview','offer','final_review')",
                String.class);
        assertThat(foreignKeys).contains(
                "fk_application_user", "fk_application_job", "fk_application_resume_version_owner",
                "fk_application_stage_history_user", "fk_application_stage_history_application_owner",
                "fk_assessment_user", "fk_assessment_application_owner",
                "fk_interview_user", "fk_interview_application_owner",
                "fk_offer_user", "fk_offer_application_owner",
                "fk_final_review_user", "fk_final_review_application_owner");

        List<String> uniqueIndexes = jdbcTemplate.queryForList(
                "SELECT DISTINCT index_name FROM information_schema.statistics WHERE table_schema = DATABASE() "
                        + "AND non_unique = 0 AND table_name IN ('application','offer','final_review')",
                String.class);
        assertThat(uniqueIndexes).contains("uk_application_id_user_id", "uk_application_user_ongoing_job_id",
                "uk_offer_application_id", "uk_final_review_application_id");

        List<String> resumeOwnerColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.key_column_usage WHERE constraint_schema = DATABASE() "
                        + "AND table_name = 'application' AND constraint_name = 'fk_application_resume_version_owner' "
                        + "ORDER BY ordinal_position",
                String.class);
        assertThat(resumeOwnerColumns).containsExactly("resume_version_id", "user_id");

        List<String> childOwnerForeignKeys = List.of(
                "fk_application_stage_history_application_owner", "fk_assessment_application_owner",
                "fk_interview_application_owner", "fk_offer_application_owner",
                "fk_final_review_application_owner");
        for (String foreignKey : childOwnerForeignKeys) {
            Integer columnCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema = DATABASE() "
                            + "AND constraint_name = ? AND referenced_table_name = 'application'",
                    Integer.class, foreignKey);
            assertThat(columnCount).as(foreignKey).isEqualTo(2);
        }

        GeneratedColumnMetadata generatedColumn = jdbcTemplate.queryForObject(
                "SELECT extra, generation_expression FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = 'application' AND column_name = 'ongoing_job_id'",
                (resultSet, rowNumber) -> new GeneratedColumnMetadata(
                        resultSet.getString("extra"), resultSet.getString("generation_expression")));
        assertThat(generatedColumn.extra()).containsIgnoringCase("STORED GENERATED");
        assertThat(generatedColumn.expression()).containsIgnoringCase("current_stage").containsIgnoringCase("job_id");

        assertThat(applicationMapper).isNotNull();
        assertThat(applicationStageHistoryMapper).isNotNull();
        assertThat(assessmentMapper).isNotNull();
        assertThat(interviewMapper).isNotNull();
        assertThat(offerMapper).isNotNull();
        assertThat(finalReviewMapper).isNotNull();
    }

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

    @Test
    void milestoneThreeLearningTablesConstraintsAndMappersShouldExistInRealMySql() {
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
        List<LearningForeignKeyColumn> learningForeignKeyColumns = jdbcTemplate.query(
                "SELECT table_name, constraint_name, ordinal_position, column_name, "
                        + "referenced_table_name, referenced_column_name "
                        + "FROM information_schema.key_column_usage "
                        + "WHERE constraint_schema = DATABASE() "
                        + "AND table_name IN ('learning_plan', 'learning_task', 'study_record', 'weekly_review', "
                        + "'learning_note', 'learning_material') "
                        + "AND referenced_table_name IS NOT NULL "
                        + "ORDER BY table_name, constraint_name, ordinal_position",
                (resultSet, rowNumber) -> new LearningForeignKeyColumn(
                        resultSet.getString("table_name"),
                        resultSet.getString("constraint_name"),
                        resultSet.getInt("ordinal_position"),
                        resultSet.getString("column_name"),
                        resultSet.getString("referenced_table_name"),
                        resultSet.getString("referenced_column_name")
                )
        );
        List<LearningUniqueIndexColumn> learningUniqueIndexColumns = jdbcTemplate.query(
                "SELECT table_name, index_name, seq_in_index, column_name "
                        + "FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() "
                        + "AND table_name IN ('learning_plan', 'learning_task', 'study_record', 'weekly_review', "
                        + "'learning_note', 'learning_material') "
                        + "AND non_unique = 0 AND index_name <> 'PRIMARY' "
                        + "ORDER BY table_name, index_name, seq_in_index",
                (resultSet, rowNumber) -> new LearningUniqueIndexColumn(
                        resultSet.getString("table_name"),
                        resultSet.getString("index_name"),
                        resultSet.getInt("seq_in_index"),
                        resultSet.getString("column_name")
                )
        );
        List<LearningTableMetadata> learningTableMetadata = jdbcTemplate.query(
                "SELECT table_name, engine, table_collation FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() "
                        + "AND table_name IN ('learning_plan', 'learning_task', 'study_record', 'weekly_review', "
                        + "'learning_note', 'learning_material') "
                        + "ORDER BY table_name",
                (resultSet, rowNumber) -> new LearningTableMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("engine"),
                        resultSet.getString("table_collation")
                )
        );
        List<LearningForeignKeyRule> learningForeignKeyRules = jdbcTemplate.query(
                "SELECT table_name, constraint_name, delete_rule "
                        + "FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = DATABASE() "
                        + "AND table_name IN ('learning_plan', 'learning_task', 'study_record', 'weekly_review', "
                        + "'learning_note', 'learning_material') "
                        + "ORDER BY table_name, constraint_name",
                (resultSet, rowNumber) -> new LearningForeignKeyRule(
                        resultSet.getString("table_name"),
                        resultSet.getString("constraint_name"),
                        resultSet.getString("delete_rule")
                )
        );
        LearningMaterialSourceUrlMetadata learningMaterialSourceUrl = jdbcTemplate.queryForObject(
                "SELECT data_type, character_maximum_length, is_nullable "
                        + "FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'learning_material' "
                        + "AND column_name = 'source_url'",
                (resultSet, rowNumber) -> new LearningMaterialSourceUrlMetadata(
                        resultSet.getString("data_type"),
                        resultSet.getLong("character_maximum_length"),
                        resultSet.getString("is_nullable")
                )
        );

        assertThat(tables).containsAll(MILESTONE_THREE_TABLES);
        assertThat(foreignKeys).containsAll(REQUIRED_LEARNING_FOREIGN_KEYS);
        assertThat(uniqueIndexes).containsAll(REQUIRED_LEARNING_UNIQUE_INDEXES);
        assertThat(learningForeignKeyColumns).containsExactlyElementsOf(EXPECTED_LEARNING_FOREIGN_KEY_COLUMNS);
        assertThat(learningUniqueIndexColumns).containsExactlyElementsOf(EXPECTED_LEARNING_UNIQUE_INDEX_COLUMNS);
        assertThat(learningTableMetadata).containsExactlyElementsOf(EXPECTED_LEARNING_TABLE_METADATA);
        assertThat(learningForeignKeyRules).containsExactlyElementsOf(EXPECTED_LEARNING_FOREIGN_KEY_RULES);
        assertThat(learningMaterialSourceUrl).isEqualTo(EXPECTED_LEARNING_MATERIAL_SOURCE_URL);
        assertThat(learningPlanMapper).isNotNull();
        assertThat(learningTaskMapper).isNotNull();
        assertThat(studyRecordMapper).isNotNull();
        assertThat(weeklyReviewMapper).isNotNull();
        assertThat(learningNoteMapper).isNotNull();
        assertThat(learningMaterialMapper).isNotNull();
    }

    @Test
    void milestoneFourResumeTablesOwnerAwareConstraintsMetadataAndMappersShouldExistInRealMySql() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class
        );
        List<String> foreignKeys = jdbcTemplate.queryForList(
                "SELECT constraint_name FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = DATABASE()",
                String.class
        );
        List<ResumeForeignKeyColumn> resumeForeignKeyColumns = jdbcTemplate.query(
                "SELECT table_name, constraint_name, ordinal_position, column_name, "
                        + "referenced_table_name, referenced_column_name "
                        + "FROM information_schema.key_column_usage "
                        + "WHERE constraint_schema = DATABASE() "
                        + "AND table_name IN ('resume', 'resume_version', 'resume_content_item') "
                        + "AND referenced_table_name IS NOT NULL "
                        + "ORDER BY table_name, constraint_name, ordinal_position",
                (resultSet, rowNumber) -> new ResumeForeignKeyColumn(
                        resultSet.getString("table_name"),
                        resultSet.getString("constraint_name"),
                        resultSet.getInt("ordinal_position"),
                        resultSet.getString("column_name"),
                        resultSet.getString("referenced_table_name"),
                        resultSet.getString("referenced_column_name")
                )
        );
        List<ResumeUniqueIndexColumn> resumeUniqueIndexColumns = jdbcTemplate.query(
                "SELECT table_name, index_name, seq_in_index, column_name "
                        + "FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'resume_version' "
                        + "AND index_name = 'uk_resume_version_resume_no' AND non_unique = 0 "
                        + "ORDER BY seq_in_index",
                (resultSet, rowNumber) -> new ResumeUniqueIndexColumn(
                        resultSet.getString("table_name"),
                        resultSet.getString("index_name"),
                        resultSet.getInt("seq_in_index"),
                        resultSet.getString("column_name")
                )
        );
        List<ResumeTableMetadata> resumeTableMetadata = jdbcTemplate.query(
                "SELECT table_name, engine, table_collation FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() "
                        + "AND table_name IN ('resume', 'resume_version', 'resume_content_item') "
                        + "ORDER BY table_name",
                (resultSet, rowNumber) -> new ResumeTableMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("engine"),
                        resultSet.getString("table_collation")
                )
        );

        assertThat(tables).containsAll(MILESTONE_FOUR_RESUME_TABLES);
        assertThat(foreignKeys).containsAll(REQUIRED_RESUME_FOREIGN_KEYS);
        assertThat(resumeForeignKeyColumns).containsExactlyElementsOf(EXPECTED_RESUME_FOREIGN_KEY_COLUMNS);
        assertThat(resumeUniqueIndexColumns).containsExactlyElementsOf(EXPECTED_RESUME_UNIQUE_INDEX_COLUMNS);
        assertThat(resumeTableMetadata).containsExactlyElementsOf(EXPECTED_RESUME_TABLE_METADATA);
        assertThat(resumeMapper).isNotNull();
        assertThat(resumeVersionMapper).isNotNull();
        assertThat(resumeContentItemMapper).isNotNull();
    }

    private record LearningForeignKeyColumn(
            String tableName,
            String constraintName,
            int ordinalPosition,
            String columnName,
            String referencedTableName,
            String referencedColumnName
    ) {
    }

    private record LearningUniqueIndexColumn(
            String tableName,
            String indexName,
            int sequenceInIndex,
            String columnName
    ) {
    }

    private record LearningTableMetadata(String tableName, String engine, String tableCollation) {
    }

    private record LearningForeignKeyRule(String tableName, String constraintName, String deleteRule) {
    }

    private record LearningMaterialSourceUrlMetadata(String dataType, Long characterMaximumLength, String isNullable) {
    }

    private record ResumeForeignKeyColumn(
            String tableName,
            String constraintName,
            int ordinalPosition,
            String columnName,
            String referencedTableName,
            String referencedColumnName
    ) {
    }

    private record ResumeUniqueIndexColumn(
            String tableName,
            String indexName,
            int sequenceInIndex,
            String columnName
    ) {
    }

    private record ResumeTableMetadata(String tableName, String engine, String tableCollation) {
    }

    private record GeneratedColumnMetadata(String extra, String expression) {
    }
}
