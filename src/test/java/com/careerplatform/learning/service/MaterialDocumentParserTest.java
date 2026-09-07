package com.careerplatform.learning.service;

import com.careerplatform.common.exception.InvalidRequestException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialDocumentParserTest {

    private final MaterialDocumentParser parser = new MaterialDocumentParser();

    @Test
    void rejectsEmptyOversizedAndMalformedFiles() {
        for (byte[] bytes : new byte[][]{new byte[0], new byte[MaterialDocumentParser.MAX_FILE_SIZE + 1], "%PDF-invalid".getBytes(StandardCharsets.UTF_8)})
            assertThatThrownBy(() -> parser.parse(bytes,"bad.pdf","application/pdf")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> parser.parse(docx(" "),"empty.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .isInstanceOf(InvalidRequestException.class).hasMessage("文档不包含可检索文本");
    }

    @Test
    void refusesRealDtdAndNonWordXml() {
        for (String xml : List.of("<!DOCTYPE w:document [<!ENTITY x SYSTEM 'file:///not-read'>]><w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:body><w:p><w:r><w:t>&x;</w:t></w:r></w:p></w:body></w:document>",
                "<document><p><t>not Word</t></p></document>"))
            assertThatThrownBy(() -> parser.parse(zipXml(xml),"bad.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void recognizesCdataTabsAndParagraphNamespace() {
        var chunks=parser.parse(zipXml("<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' xmlns:x='urn:other'><w:body><x:p><x:t>excluded</x:t></x:p><w:p><w:r><w:t><![CDATA[Java]]></w:t><w:tab/><w:t>21</w:t></w:r></w:p></w:body></w:document>"),
                "good.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(chunks).hasSize(1); assertThat(chunks.getFirst().text()).isEqualTo("Java\t21");
        assertThat(chunks.getFirst().locationLabel()).isEqualTo("Paragraph 1");
    }

    @Test
    void rejectsZipExpansionAndTooManyChunks() {
        assertThatThrownBy(() -> parser.parse(zipXml("x".repeat(MaterialDocumentParser.MAX_EXTRACTED_ZIP_BYTES+1)),
                "bomb.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .isInstanceOf(InvalidRequestException.class).hasMessage("DOCX 解压后大小不能超过10MiB");
        assertThatThrownBy(() -> parser.parse(docx(java.util.Collections.nCopies(129,"small").toArray(String[]::new)),
                "chunks.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .isInstanceOf(InvalidRequestException.class).hasMessage("文档分片数不能超过128");
    }

    @Test
    void rejectsPdfPageLimitAndEncryptedDocument() throws Exception {
        try (PDDocument document=new PDDocument(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            for(int i=0;i<101;i++) document.addPage(new PDPage()); document.save(out);
            assertThatThrownBy(() -> parser.parse(out.toByteArray(),"pages.pdf","application/pdf"))
                    .isInstanceOf(InvalidRequestException.class).hasMessage("PDF 文件页数不能超过100页");
        }
        try (PDDocument document=new PDDocument(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.protect(new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("synthetic-owner","synthetic-reader",new org.apache.pdfbox.pdmodel.encryption.AccessPermission()));
            document.save(out);
            assertThatThrownBy(() -> parser.parse(out.toByteArray(),"encrypted.pdf","application/pdf"))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    @Test
    void parsesPdfTextWithPageLocation() throws Exception {
        List<MaterialDocumentParser.ParsedChunk> chunks = parser.parse(
                pdf("Java 21 and Spring Boot"), "lesson.pdf", "application/pdf");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().text()).contains("Java 21");
        assertThat(chunks.getFirst().locationLabel()).isEqualTo("Page 1");
        assertThat(chunks.getFirst().pageNumber()).isEqualTo(1);
    }

    @Test
    void parsesDocxParagraphsAndKeepsChunkOrderWithoutInventingPages() {
        String second = "b".repeat(1_001);
        List<MaterialDocumentParser.ParsedChunk> chunks = parser.parse(
                docx("first paragraph", second), "lesson.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        assertThat(chunks).hasSize(3);
        assertThat(chunks).extracting(MaterialDocumentParser.ParsedChunk::chunkIndex)
                .containsExactly(0, 1, 2);
        assertThat(chunks.get(0).locationLabel()).isEqualTo("Paragraph 1");
        assertThat(chunks.get(1).locationLabel()).isEqualTo("Paragraph 2");
        assertThat(chunks.get(2).locationLabel()).isEqualTo("Paragraph 2");
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text().length()).isLessThanOrEqualTo(1_000));
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.pageNumber()).isNull());
    }

    @Test
    void rejectsUnsupportedMimeAndExtensionBeforeParsing() {
        assertThatThrownBy(() -> parser.parse("plain text".getBytes(StandardCharsets.UTF_8),
                "lesson.txt", "text/plain"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("仅支持 PDF 或 DOCX 文件");
    }

    @Test
    void rejectsDtdAndTextOverLimit() {
        assertThatThrownBy(() -> parser.parse(
                docx("<!DOCTYPE document [<!ENTITY x 'blocked'>]>&x;"), "lesson.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .isInstanceOf(InvalidRequestException.class);

        assertThatThrownBy(() -> parser.parse(
                docx("x".repeat(MaterialDocumentParser.MAX_TEXT_CHARS + 1)), "lesson.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("文档文本不能超过100000个字符");
    }

    private byte[] pdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] docx(String... paragraphs) {
        StringBuilder body = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
        for (String paragraph : paragraphs) {
            body.append("<w:p><w:r><w:t>").append(paragraph).append("</w:t></w:r></w:p>");
        }
        body.append("</w:body></w:document>");
        return zipXml(body.toString());
    }

    private byte[] zipXml(String xml) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
