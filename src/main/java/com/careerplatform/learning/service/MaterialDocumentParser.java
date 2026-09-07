package com.careerplatform.learning.service;

import com.careerplatform.common.exception.InvalidRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parses the two deliberately bounded document formats accepted by learning
 * material ingestion. The parser never writes an uploaded archive to disk.
 */
@Component
public class MaterialDocumentParser {

    public static final int MAX_FILE_SIZE = 5 * 1024 * 1024;
    public static final int MAX_EXTRACTED_ZIP_BYTES = 10 * 1024 * 1024;
    public static final int MAX_ZIP_ENTRIES = 200;
    public static final int MAX_PDF_PAGES = 100;
    public static final int MAX_TEXT_CHARS = 100_000;
    public static final int MAX_CHUNK_CHARS = 1_000;
    public static final int MAX_CHUNKS = 128;

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String DOCX_TRANSITIONAL_NAMESPACE =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String DOCX_STRICT_NAMESPACE =
            "http://purl.oclc.org/ooxml/wordprocessingml/main";

    public List<ParsedChunk> parse(byte[] bytes, String filename, String contentType) {
        validateSize(bytes);

        String normalizedName = normalizeFilename(filename);
        String normalizedType = normalizeContentType(contentType);
        boolean pdf = normalizedName.endsWith(".pdf") && PDF_CONTENT_TYPE.equals(normalizedType);
        boolean docx = normalizedName.endsWith(".docx") && DOCX_CONTENT_TYPE.equals(normalizedType);
        if (!pdf && !docx) {
            throw invalid("仅支持 PDF 或 DOCX 文件");
        }

        try {
            if (pdf) {
                if (!hasPdfSignature(bytes)) {
                    throw invalid("文档解析失败");
                }
                return parsePdf(bytes);
            }
            if (!hasZipSignature(bytes)) {
                throw invalid("文档解析失败");
            }
            return parseDocx(bytes);
        } catch (InvalidRequestException exception) {
            throw exception;
        } catch (TextLimitExceededException exception) {
            throw invalid("文档文本不能超过100000个字符");
        } catch (Exception exception) {
            // Do not expose parser/library diagnostics or archive contents.
            throw invalid("文档解析失败");
        }
    }

    private List<ParsedChunk> parsePdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw invalid("PDF 文件已加密，无法解析");
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount > MAX_PDF_PAGES) {
                throw invalid("PDF 文件页数不能超过100页");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            TextBudget budget = new TextBudget(MAX_TEXT_CHARS);
            List<TextBlock> blocks = new ArrayList<>();
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                BoundedTextWriter writer = new BoundedTextWriter(budget);
                try {
                    stripper.writeText(document, writer);
                } catch (TextLimitExceededException exception) {
                    throw invalid("文档文本不能超过100000个字符");
                }
                String text = normalizeText(writer.toString());
                if (!text.isEmpty()) {
                    blocks.add(new TextBlock(text, "Page " + page, page));
                }
            }
            return chunk(blocks);
        }
    }

    private List<ParsedChunk> parseDocx(byte[] bytes) throws IOException, XMLStreamException {
        byte[] documentXml = null;
        int entries = 0;
        int extractedBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ZIP_ENTRIES) {
                    throw invalid("DOCX 文件条目数不能超过200");
                }

                ByteArrayOutputStream current = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    extractedBytes += read;
                    if (extractedBytes > MAX_EXTRACTED_ZIP_BYTES) {
                        throw invalid("DOCX 解压后大小不能超过10MiB");
                    }
                    // Only document.xml is retained; other entries are still
                    // fully bounded and consumed so the ZIP stream can advance.
                if ("word/document.xml".equals(entry.getName())) {
                    if (documentXml != null) {
                        throw invalid("文档解析失败");
                    }
                    current.write(buffer, 0, read);
                }
                }
                if ("word/document.xml".equals(entry.getName())) {
                    documentXml = current.toByteArray();
                }
                zip.closeEntry();
            }
        }

        if (documentXml == null || documentXml.length == 0) {
            throw invalid("文档解析失败");
        }
        try {
            return chunk(parseWordParagraphs(documentXml));
        } catch (TextLimitExceededException exception) {
            throw invalid("文档文本不能超过100000个字符");
        }
    }

    private List<TextBlock> parseWordParagraphs(byte[] documentXml) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        setXmlSecurity(factory);
        List<TextBlock> paragraphs = new ArrayList<>();
        XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(documentXml));
        StringBuilder current = null;
        TextBudget budget = new TextBudget(MAX_TEXT_CHARS);
        boolean inText = false;
        boolean rootSeen = false;
        int paragraphNumber = 0;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String localName = reader.getLocalName();
                    if (!rootSeen) {
                        rootSeen = true;
                        if (!"document".equals(localName)
                                || !isWordprocessingNamespace(reader.getNamespaceURI())) {
                            throw invalid("文档解析失败");
                        }
                    }
                    if ("p".equals(localName) && isWordprocessingNamespace(reader.getNamespaceURI())) {
                        if (current != null) {
                            addParagraph(paragraphs, current, paragraphNumber);
                        }
                        current = new StringBuilder();
                        paragraphNumber++;
                    } else if (current != null && "t".equals(localName)
                            && isWordprocessingNamespace(reader.getNamespaceURI())) {
                        inText = true;
                    } else if (current != null && "tab".equals(localName)
                            && isWordprocessingNamespace(reader.getNamespaceURI())) {
                        budget.accept(1);
                        current.append('\t');
                    } else if (current != null && ("br".equals(localName) || "cr".equals(localName))
                            && isWordprocessingNamespace(reader.getNamespaceURI())) {
                        budget.accept(1);
                        current.append('\n');
                    }
                } else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
                        && current != null && inText) {
                    int length = reader.getTextLength();
                    budget.accept(length);
                    current.append(reader.getTextCharacters(), reader.getTextStart(), length);
                } else if (event == XMLStreamConstants.END_ELEMENT
                        && current != null) {
                    if ("t".equals(reader.getLocalName())
                            && isWordprocessingNamespace(reader.getNamespaceURI())) {
                        inText = false;
                    } else if ("p".equals(reader.getLocalName())
                            && isWordprocessingNamespace(reader.getNamespaceURI())) {
                        inText = false;
                        addParagraph(paragraphs, current, paragraphNumber);
                        current = null;
                    }
                } else if (event == XMLStreamConstants.DTD
                        || event == XMLStreamConstants.ENTITY_REFERENCE) {
                    throw invalid("文档解析失败");
                }
            }
            if (current != null) {
                addParagraph(paragraphs, current, paragraphNumber);
            }
        } finally {
            reader.close();
        }
        return paragraphs;
    }

    private void addParagraph(List<TextBlock> paragraphs, StringBuilder value, int number) {
        String text = normalizeText(value.toString());
        if (!text.isEmpty()) {
            paragraphs.add(new TextBlock(text, "Paragraph " + number, null));
        }
    }

    private List<ParsedChunk> chunk(List<TextBlock> blocks) {
        List<ParsedChunk> result = new ArrayList<>();
        for (TextBlock block : blocks) {
            for (int start = 0; start < block.text().length(); start += MAX_CHUNK_CHARS) {
                if (result.size() >= MAX_CHUNKS) {
                    throw invalid("文档分片数不能超过128");
                }
                int end = Math.min(start + MAX_CHUNK_CHARS, block.text().length());
                result.add(new ParsedChunk(result.size(), block.text().substring(start, end),
                        block.locationLabel(), block.pageNumber()));
            }
        }
        if (result.isEmpty()) {
            throw invalid("文档不包含可检索文本");
        }
        return List.copyOf(result);
    }

    private static void setXmlSecurity(XMLInputFactory factory) {
        try {
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        } catch (IllegalArgumentException exception) {
            throw invalid("文档解析失败");
        }
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static void validateSize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw invalid("上传文件不能为空");
        }
        if (bytes.length > MAX_FILE_SIZE) {
            throw invalid("文件大小不能超过5MiB");
        }
    }

    private static String normalizeFilename(String filename) {
        return filename == null ? "" : filename.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        String value = separator < 0 ? contentType : contentType.substring(0, separator);
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasPdfSignature(byte[] bytes) {
        return bytes.length >= 5
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F'
                && bytes[4] == '-';
    }

    private static boolean hasZipSignature(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == 'P'
                && bytes[1] == 'K'
                && bytes[2] == 3
                && bytes[3] == 4;
    }

    private static InvalidRequestException invalid(String message) {
        return new InvalidRequestException(Objects.requireNonNull(message, "message must not be null"));
    }

    private record TextBlock(String text, String locationLabel, Integer pageNumber) { }

    private static boolean isWordprocessingNamespace(String namespace) {
        return DOCX_TRANSITIONAL_NAMESPACE.equals(namespace) || DOCX_STRICT_NAMESPACE.equals(namespace);
    }

    private static final class TextBudget {
        private final int maximum;
        private int used;

        private TextBudget(int maximum) {
            this.maximum = maximum;
        }

        private void accept(int length) throws TextLimitExceededException {
            if (length < 0 || length > maximum - used) {
                throw new TextLimitExceededException();
            }
            used += length;
        }
    }

    private static final class TextLimitExceededException extends RuntimeException {
    }

    private static final class BoundedTextWriter extends Writer {
        private final TextBudget budget;
        private final StringBuilder value = new StringBuilder();

        private BoundedTextWriter(TextBudget budget) {
            this.budget = budget;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            budget.accept(len);
            value.append(cbuf, off, len);
        }

        @Override
        public void flush() { }

        @Override
        public void close() { }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    public record ParsedChunk(int chunkIndex, String text, String locationLabel, Integer pageNumber) { }
}
