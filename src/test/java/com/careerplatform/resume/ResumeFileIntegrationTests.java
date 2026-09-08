package com.careerplatform.resume;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careerplatform.resume.entity.ResumeFile;
import com.careerplatform.resume.mapper.ResumeFileMapper;
import com.careerplatform.user.mapper.AppUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.MimeTypeUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResumeFileIntegrationTests {

    private static final String PDF = "application/pdf";
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResumeFileMapper resumeFileMapper;

    @Autowired
    private AppUserMapper appUserMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Set<Long> createdUserIds = new HashSet<>();

    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM application_stage_history WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM application WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM resume_file WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM resume_content_item WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM resume_version WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM resume WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM job_requirement WHERE job_id IN (SELECT id FROM job WHERE user_id = ?)", userId);
            jdbcTemplate.update("DELETE FROM job_note WHERE job_id IN (SELECT id FROM job WHERE user_id = ?)", userId);
            jdbcTemplate.update("DELETE FROM job WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM company WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM user_skill WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM education_experience WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM project_experience WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM internship_experience WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM certificate_award WHERE user_id = ?", userId);
            appUserMapper.deleteById(userId);
        }
    }

    @Test
    void uploadedPdfAndDocxCanBeFinalizedAndBoundToApplicationsWithoutContentItems() throws Exception {
        AuthSession owner = registerAndLogin("rfa_owner_");
        AuthSession other = registerAndLogin("rfa_other_");
        Long companyId = createCompany(owner, "File application company");
        Long pdfJobId = createJob(owner, companyId, "PDF file application job");
        Long docxJobId = createJob(owner, companyId, "DOCX file application job");
        Long otherCompanyId = createCompany(other, "Other file application company");
        Long otherJobId = createJob(other, otherCompanyId, "Other file application job");

        UploadIds pdf = uploadVersion(owner, "application.pdf", validPdfBytes());
        UploadIds docx = uploadVersion(owner, "application.docx", validDocxBytes());
        assertThat(contentItemCount(pdf.versionId())).isZero();
        assertThat(contentItemCount(docx.versionId())).isZero();

        mockMvc.perform(post("/api/v1/applications")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(pdfJobId, pdf.versionId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESOURCE_STATE"));

        finalizeVersion(owner, pdf);
        finalizeVersion(owner, docx);
        assertThat(contentItemCount(pdf.versionId())).isZero();
        assertThat(contentItemCount(docx.versionId())).isZero();

        mockMvc.perform(post("/api/v1/applications")
                        .header("Authorization", other.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(otherJobId, pdf.versionId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        JsonNode pdfApplication = responseJson(mockMvc.perform(post("/api/v1/applications")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(pdfJobId, pdf.versionId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentStage").value("APPLIED"))
                .andExpect(jsonPath("$.resumeVersionId").value(pdf.versionId())));
        assertPersistedApplicationVersion(pdfApplication, pdf.versionId());

        JsonNode docxApplication = responseJson(mockMvc.perform(post("/api/v1/applications")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(docxJobId, docx.versionId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentStage").value("APPLIED"))
                .andExpect(jsonPath("$.resumeVersionId").value(docx.versionId())));
        assertPersistedApplicationVersion(docxApplication, docx.versionId());
    }

    @Test
    void pdfUploadShouldCreateResumeVersionAndFile() throws Exception {
        AuthSession owner = registerAndLogin("resume_file_pdf_");
        byte[] bytes = pdfBytes();

        JsonNode response = responseJson(mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(filePart("file", "../candidate.PDF", PDF, bytes))
                        .file(textPart("name", "Uploaded resume"))
                        .file(textPart("description", "source description"))
                        .file(textPart("versionLabel", "PDF v1"))
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resume.id").isNumber())
                .andExpect(jsonPath("$.version.versionNo").value(1))
                .andExpect(jsonPath("$.version.status").value("DRAFT"))
                .andExpect(jsonPath("$.file.originalFilename").value("candidate.PDF"))
                .andExpect(jsonPath("$.file.contentType").value(PDF))
                .andExpect(jsonPath("$.file.fileData").doesNotExist())
                .andExpect(jsonPath("$.file.userId").doesNotExist()));

        Long versionId = response.at("/version/id").asLong();
        ResumeFile saved = resumeFileMapper.selectOne(new LambdaQueryWrapper<ResumeFile>()
                .eq(ResumeFile::getResumeVersionId, versionId)
                .eq(ResumeFile::getUserId, owner.userId()));
        assertThat(saved).isNotNull();
        assertThat(saved.getFileData()).containsExactly(bytes);
    }

    @Test
    void docxUploadShouldBeAcceptedWithoutParsing正文() throws Exception {
        AuthSession owner = registerAndLogin("resume_file_docx_");
        mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(filePart("file", "resume.docx", DOCX, docxBytes(true, true)))
                        .file(textPart("name", "DOCX resume"))
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file.contentType").value(DOCX))
                .andExpect(jsonPath("$.file.originalFilename").value("resume.docx"));
    }

    @Test
    void existingResumeUploadShouldUseNextVersionNumber() throws Exception {
        AuthSession owner = registerAndLogin("resume_file_next_");
        Long resumeId = createResume(owner);
        mockMvc.perform(multipart("/api/v1/resumes/{resumeId}/versions/upload", resumeId)
                        .file(filePart("file", "first.pdf", PDF, pdfBytes()))
                        .file(textPart("label", "first"))
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version.versionNo").value(1));
        mockMvc.perform(multipart("/api/v1/resumes/{resumeId}/versions/upload", resumeId)
                        .file(filePart("file", "second.pdf", PDF, pdfBytes()))
                        .file(textPart("label", "second"))
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version.versionNo").value(2));
    }

    @Test
    void metadataEndpointShouldReturnOnlySafeMetadata() throws Exception {
        AuthSession owner = registerAndLogin("resume_file_meta_");
        UploadIds ids = uploadVersion(owner, "metadata.pdf", pdfBytes());
        mockMvc.perform(get("/api/v1/resumes/{resumeId}/versions/{versionId}/file",
                        ids.resumeId(), ids.versionId())
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(ids.versionId()))
                .andExpect(jsonPath("$.originalFilename").value("metadata.pdf"))
                .andExpect(jsonPath("$.contentType").value(PDF))
                .andExpect(jsonPath("$.fileSize").value(pdfBytes().length))
                .andExpect(jsonPath("$.fileData").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void downloadShouldPreserveBytesAndSafeHeaders() throws Exception {
        AuthSession owner = registerAndLogin("resume_file_download_");
        byte[] bytes = pdfBytes();
        UploadIds ids = uploadVersion(owner, "我的简历.pdf", bytes);
        mockMvc.perform(get("/api/v1/resumes/{resumeId}/versions/{versionId}/file/download",
                        ids.resumeId(), ids.versionId())
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", PDF))
                .andExpect(header().string("Content-Length", String.valueOf(bytes.length)))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("filename")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).containsExactly(bytes));
    }

    @Test
    void unsupportedExtensionShouldBeRejected() throws Exception {
        AuthSession owner = registerAndLogin("resume_file_ext_");
        mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(filePart("file", "resume.txt", "text/plain", "plain".getBytes(StandardCharsets.UTF_8)))
                        .file(textPart("name", "Invalid"))
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void fakePdfShouldBeRejected() throws Exception {
        AuthSession owner = registerAndLogin("resume_file_fake_pdf_");
        mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(filePart("file", "resume.pdf", PDF, "not a pdf".getBytes(StandardCharsets.UTF_8)))
                        .file(textPart("name", "Fake PDF"))
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fakeDocxWithoutCoreEntriesShouldBeRejected() throws Exception {
        AuthSession owner = registerAndLogin("resume_file_fake_docx_");
        mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(filePart("file", "resume.docx", DOCX, docxBytes(false, false)))
                        .file(textPart("name", "Fake DOCX"))
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyFileShouldBeRejected() throws Exception {
        AuthSession owner = registerAndLogin("resume_file_empty_");
        mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(filePart("file", "empty.pdf", PDF, new byte[0]))
                        .file(textPart("name", "Empty"))
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fileLargerThanFiveMiBShouldBeRejected() throws Exception {
        AuthSession owner = registerAndLogin("resume_file_large_");
        byte[] bytes = new byte[5 * 1024 * 1024 + 1];
        bytes[0] = '%';
        bytes[1] = 'P';
        bytes[2] = 'D';
        bytes[3] = 'F';
        bytes[4] = '-';
        mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(filePart("file", "large.pdf", PDF, bytes))
                        .file(textPart("name", "Large"))
                        .header("Authorization", owner.authorization()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void anotherOwnerCannotReadMetadataOrDownload() throws Exception {
        AuthSession owner = registerAndLogin("resume_file_owner_");
        AuthSession other = registerAndLogin("resume_file_other_");
        UploadIds ids = uploadVersion(owner, "owned.pdf", pdfBytes());
        mockMvc.perform(get("/api/v1/resumes/{resumeId}/versions/{versionId}/file",
                        ids.resumeId(), ids.versionId())
                        .header("Authorization", other.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/resumes/{resumeId}/versions/{versionId}/file/download",
                        ids.resumeId(), ids.versionId())
                        .header("Authorization", other.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(multipart("/api/v1/resumes/{resumeId}/versions/upload", ids.resumeId())
                        .file(filePart("file", "fake.pdf", PDF,
                                "not a pdf".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", other.authorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void copyVersionShouldCopyFileBytesIntoIndependentRow() throws Exception {
        AuthSession owner = registerAndLogin("resume_file_copy_");
        byte[] bytes = pdfBytes();
        UploadIds source = uploadVersion(owner, "source.pdf", bytes);
        Long sourceFileId = file(source.versionId(), owner.userId()).getId();
        JsonNode target = responseJson(mockMvc.perform(post("/api/v1/resumes/{resumeId}/versions/{versionId}/copy",
                        source.resumeId(), source.versionId())
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"copy\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT")));
        Long targetVersionId = target.get("id").asLong();
        ResumeFile targetFile = file(targetVersionId, owner.userId());
        assertThat(targetFile).isNotNull();
        assertThat(targetFile.getId()).isNotEqualTo(sourceFileId);
        assertThat(targetFile.getFileData()).containsExactly(bytes);
    }

    @Test
    void deletingDraftVersionShouldDeleteItsFileFirst() throws Exception {
        AuthSession owner = registerAndLogin("resume_file_delver_");
        UploadIds ids = uploadVersion(owner, "delete.pdf", pdfBytes());
        mockMvc.perform(delete("/api/v1/resumes/{resumeId}/versions/{versionId}", ids.resumeId(), ids.versionId())
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNoContent());
        assertThat(file(ids.versionId(), owner.userId())).isNull();
    }

    @Test
    void deletingDraftOnlyResumeShouldDeleteItsFiles() throws Exception {
        AuthSession owner = registerAndLogin("resume_file_delete_resume_");
        UploadIds ids = uploadVersion(owner, "delete-resume.pdf", pdfBytes());
        mockMvc.perform(delete("/api/v1/resumes/{resumeId}", ids.resumeId())
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isNoContent());
        assertThat(file(ids.versionId(), owner.userId())).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM resume WHERE id = ?", Integer.class,
                ids.resumeId())).isZero();
    }

    private UploadIds uploadVersion(AuthSession owner, String filename, byte[] bytes) throws Exception {
        JsonNode response = responseJson(mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(filePart("file", filename, filename.endsWith("docx") ? DOCX : PDF, bytes))
                        .file(textPart("name", "Resume " + filename))
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version.status").value("DRAFT"))
                .andExpect(jsonPath("$.file.fileData").doesNotExist()));
        return new UploadIds(response.at("/resume/id").asLong(), response.at("/version/id").asLong());
    }

    private void finalizeVersion(AuthSession owner, UploadIds ids) throws Exception {
        mockMvc.perform(post("/api/v1/resumes/{resumeId}/versions/{versionId}/finalize",
                        ids.resumeId(), ids.versionId())
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"));
    }

    private void assertPersistedApplicationVersion(JsonNode application, Long versionId) {
        Long applicationId = application.get("id").asLong();
        Long savedVersionId = jdbcTemplate.queryForObject(
                "SELECT resume_version_id FROM application WHERE id = ?", Long.class, applicationId);
        assertThat(savedVersionId).isEqualTo(versionId);
    }

    private int contentItemCount(Long versionId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM resume_content_item WHERE version_id = ?", Integer.class, versionId);
    }

    private Long createCompany(AuthSession owner, String name) throws Exception {
        JsonNode response = responseJson(mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()));
        return response.get("id").asLong();
    }

    private Long createJob(AuthSession owner, Long companyId, String title) throws Exception {
        JsonNode response = responseJson(mockMvc.perform(post("/api/v1/jobs")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":" + companyId + ",\"title\":\"" + title
                                + "\",\"city\":\"上海\",\"jobType\":\"FULL_TIME\","
                                + "\"rawJd\":\"负责 Java 服务开发\",\"sourceType\":\"MANUAL\"}"))
                .andExpect(status().isCreated()));
        return response.get("id").asLong();
    }

    private String applicationJson(Long jobId, Long resumeVersionId) {
        return "{\"jobId\":" + jobId + ",\"resumeVersionId\":" + resumeVersionId + "}";
    }

    private Long createResume(AuthSession owner) throws Exception {
        JsonNode response = responseJson(mockMvc.perform(post("/api/v1/resumes")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Existing\"}"))
                .andExpect(status().isCreated()));
        return response.get("id").asLong();
    }

    private ResumeFile file(Long versionId, Long userId) {
        return resumeFileMapper.selectOne(new LambdaQueryWrapper<ResumeFile>()
                .eq(ResumeFile::getResumeVersionId, versionId)
                .eq(ResumeFile::getUserId, userId));
    }

    private AuthSession registerAndLogin(String prefix) throws Exception {
        String username = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String password = "correct-password";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
        JsonNode login = responseJson(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()));
        Long userId = login.get("userId").asLong();
        createdUserIds.add(userId);
        return new AuthSession(login.get("token").asText(), userId);
    }

    private JsonNode responseJson(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MockMultipartFile filePart(String name, String filename, String contentType, byte[] bytes) {
        return new MockMultipartFile(name, filename, contentType, bytes);
    }

    private MockMultipartFile textPart(String name, String value) {
        return new MockMultipartFile(name, "", MimeTypeUtils.TEXT_PLAIN_VALUE,
                value.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] pdfBytes() {
        return "%PDF-1.7\nminimal resume bytes\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    }

    private byte[] validPdfBytes() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] validDocxBytes() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zipEntry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            zipEntry(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """);
            zipEntry(zip, "word/document.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p><w:r><w:t>Resume</w:t></w:r></w:p>
                        <w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>
                      </w:body>
                    </w:document>
                    """);
        }
        return output.toByteArray();
    }

    private void zipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private byte[] docxBytes(boolean contentTypes, boolean document) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            if (contentTypes) {
                zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
                zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            if (document) {
                zip.putNextEntry(new ZipEntry("word/document.xml"));
                zip.write("<w:document/>".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private record AuthSession(String token, Long userId) {
        String authorization() {
            return "Bearer " + token;
        }
    }

    private record UploadIds(Long resumeId, Long versionId) { }
}
