package com.careerplatform.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Transactional
    void profileShouldUpsertAndReturnOnlyCurrentUsersProfile() throws Exception {
        AuthSession user = registerAndLogin("profile_");

        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Alice","phone":"13800138000","currentCity":"Shanghai","githubUrl":"https://github.com/alice"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alice"))
                .andExpect(jsonPath("$.userId").doesNotExist());

        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Alice Updated","phone":"13900139000","currentCity":"Beijing","githubUrl":"https://github.com/alice-updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alice Updated"));

        mockMvc.perform(get("/api/v1/profile").header("Authorization", user.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alice Updated"))
                .andExpect(jsonPath("$.currentCity").value("Beijing"));
    }

    @Test
    @Transactional
    void educationExperienceShouldSupportOwnedCrud() throws Exception {
        AuthSession user = registerAndLogin("education_");
        Long educationId = responseId(mockMvc.perform(post("/api/v1/education-experiences")
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(educationJson("University A", "Computer Science")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schoolName").value("University A")));

        mockMvc.perform(get("/api/v1/education-experiences").header("Authorization", user.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(educationId));
        mockMvc.perform(get("/api/v1/education-experiences/{id}", educationId)
                        .header("Authorization", user.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.major").value("Computer Science"));
        mockMvc.perform(put("/api/v1/education-experiences/{id}", educationId)
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(educationJson("University A", "Software Engineering")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.major").value("Software Engineering"));
        mockMvc.perform(delete("/api/v1/education-experiences/{id}", educationId)
                        .header("Authorization", user.authorization()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/education-experiences/{id}", educationId)
                        .header("Authorization", user.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @Transactional
    void skillAndUserSkillShouldEnforceDictionaryAndBindingUniqueness() throws Exception {
        AuthSession user = registerAndLogin("skill_");
        String skillName = "Java-" + UUID.randomUUID().toString().substring(0, 8);
        Long skillId = responseId(mockMvc.perform(post("/api/v1/skills")
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + skillName + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(skillName)));

        mockMvc.perform(post("/api/v1/skills")
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + skillName + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));

        Long userSkillId = responseId(mockMvc.perform(post("/api/v1/user-skills")
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skillId\":" + skillId + ",\"proficiency\":\"BEGINNER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skillId").value(skillId))
                .andExpect(jsonPath("$.proficiency").value("BEGINNER")));

        mockMvc.perform(post("/api/v1/user-skills")
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skillId\":" + skillId + ",\"proficiency\":\"BEGINNER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
        mockMvc.perform(put("/api/v1/user-skills/{id}", userSkillId)
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proficiency\":\"PROFICIENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proficiency").value("PROFICIENT"));
        mockMvc.perform(delete("/api/v1/user-skills/{id}", userSkillId)
                        .header("Authorization", user.authorization()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Transactional
    void projectInternshipAndCertificateShouldSupportOwnedCrud() throws Exception {
        AuthSession user = registerAndLogin("experience_");
        assertSimpleCrud(user, "/api/v1/project-experiences", """
                {"projectName":"Career Platform","role":"Developer","startDate":"2025-01-01","endDate":"2025-06-01","description":"Backend","techStack":"Java, MySQL","projectUrl":"https://example.com/project"}
                """, "projectName", "Career Platform");
        assertSimpleCrud(user, "/api/v1/internship-experiences", """
                {"companyName":"Example Inc","position":"Backend Intern","startDate":"2025-01-01","endDate":"2025-06-01","description":"Development"}
                """, "companyName", "Example Inc");
        assertSimpleCrud(user, "/api/v1/certificate-awards", """
                {"name":"Java Certificate","type":"CERTIFICATE","issuer":"Oracle","issueDate":"2025-06-01","description":"Passed"}
                """, "name", "Java Certificate");
    }

    @Test
    @Transactional
    void otherUserShouldReceiveNotFoundForPrivateResources() throws Exception {
        AuthSession owner = registerAndLogin("owner_");
        AuthSession other = registerAndLogin("other_");
        Long educationId = responseId(mockMvc.perform(post("/api/v1/education-experiences")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(educationJson("Owner University", "Computer Science")))
                .andExpect(status().isCreated()));
        Long projectId = responseId(mockMvc.perform(post("/api/v1/project-experiences")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectName":"Owner Project","role":"Developer","startDate":"2025-01-01","description":"Private","techStack":"Java"}
                                """))
                .andExpect(status().isCreated()));
        Long certificateId = responseId(mockMvc.perform(post("/api/v1/certificate-awards")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Owner Certificate","type":"CERTIFICATE","issuer":"Issuer","issueDate":"2025-06-01"}
                                """))
                .andExpect(status().isCreated()));
        Long internshipId = responseId(mockMvc.perform(post("/api/v1/internship-experiences")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Owner Company","position":"Intern","startDate":"2025-01-01","description":"Private"}
                                """))
                .andExpect(status().isCreated()));
        String skillName = "IsolationSkill-" + UUID.randomUUID().toString().substring(0, 8);
        Long skillId = responseId(mockMvc.perform(post("/api/v1/skills")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + skillName + "\"}"))
                .andExpect(status().isCreated()));
        Long userSkillId = responseId(mockMvc.perform(post("/api/v1/user-skills")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skillId\":" + skillId + ",\"proficiency\":\"BEGINNER\"}"))
                .andExpect(status().isCreated()));

        assertOtherUserCannotCrud(
                other,
                "/api/v1/education-experiences",
                educationId,
                educationJson("Stolen University", "Stolen Major")
        );
        assertOtherUserCannotCrud(
                other,
                "/api/v1/project-experiences",
                projectId,
                """
                        {"projectName":"Stolen","role":"Developer","startDate":"2025-01-01","description":"Private","techStack":"Java"}
                        """
        );
        assertOtherUserCannotCrud(
                other,
                "/api/v1/certificate-awards",
                certificateId,
                """
                        {"name":"Stolen Certificate","type":"AWARD","issuer":"Other","issueDate":"2025-06-01"}
                """
        );
        assertOtherUserCannotCrud(
                other,
                "/api/v1/internship-experiences",
                internshipId,
                """
                        {"companyName":"Stolen Company","position":"Intern","startDate":"2025-01-01","description":"Private"}
                        """
        );
        assertOtherUserCannotCrud(
                other,
                "/api/v1/user-skills",
                userSkillId,
                "{\"proficiency\":\"PROFICIENT\"}"
        );

        mockMvc.perform(get("/api/v1/education-experiences/{id}", educationId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolName").value("Owner University"))
                .andExpect(jsonPath("$.major").value("Computer Science"));
        mockMvc.perform(get("/api/v1/project-experiences/{id}", projectId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectName").value("Owner Project"));
        mockMvc.perform(get("/api/v1/certificate-awards/{id}", certificateId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Owner Certificate"))
                .andExpect(jsonPath("$.issuer").value("Issuer"));
        mockMvc.perform(get("/api/v1/internship-experiences/{id}", internshipId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Owner Company"));
        mockMvc.perform(get("/api/v1/user-skills/{id}", userSkillId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillId").value(skillId))
                .andExpect(jsonPath("$.proficiency").value("BEGINNER"));
    }

    private void assertSimpleCrud(AuthSession user, String basePath, String json, String field, String value)
            throws Exception {
        Long id = responseId(mockMvc.perform(post(basePath)
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$." + field).value(value)));
        mockMvc.perform(get(basePath + "/{id}", id).header("Authorization", user.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$." + field).value(value));
        mockMvc.perform(get(basePath).header("Authorization", user.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));
        mockMvc.perform(put(basePath + "/{id}", id)
                        .header("Authorization", user.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$." + field).value(value));
        mockMvc.perform(delete(basePath + "/{id}", id).header("Authorization", user.authorization()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(basePath + "/{id}", id).header("Authorization", user.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private void assertOtherUserCannotCrud(
            AuthSession other,
            String basePath,
            Long id,
            String updateJson) throws Exception {
        mockMvc.perform(get(basePath + "/{id}", id)
                        .header("Authorization", other.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(put(basePath + "/{id}", id)
                        .header("Authorization", other.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(delete(basePath + "/{id}", id)
                        .header("Authorization", other.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private AuthSession registerAndLogin(String prefix) throws Exception {
        String username = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String password = "correct-password";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new AuthSession(objectMapper.readTree(login).get("token").asText());
    }

    private Long responseId(org.springframework.test.web.servlet.ResultActions resultActions) throws Exception {
        String response = resultActions.andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("id").asLong();
    }

    private String educationJson(String schoolName, String major) {
        return """
                {"schoolName":"%s","major":"%s","degree":"BACHELOR","startDate":"2020-09-01","endDate":"2024-06-30","description":"Study"}
                """.formatted(schoolName, major);
    }

    private record AuthSession(String token) {
        String authorization() {
            return "Bearer " + token;
        }
    }
}
