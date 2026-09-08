package com.careerplatform.resume.service;

import com.careerplatform.common.exception.InvalidRequestException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Validates and bounds resume uploads without parsing their document contents.
 */
@Component
public class ResumeFileValidator {

    public static final int MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 200;
    private static final long MAX_EXTRACTED_ZIP_BYTES = 10L * 1024 * 1024;
    public static final String PDF_CONTENT_TYPE = "application/pdf";
    public static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    public ValidatedFile validate(MultipartFile multipartFile) {
        if (multipartFile == null) {
            throw invalid("请选择上传文件");
        }
        if (multipartFile.getSize() > MAX_FILE_SIZE) {
            throw invalid("文件大小不能超过5MiB");
        }

        byte[] bytes = readBytes(multipartFile);
        String filename = safeFilename(multipartFile.getOriginalFilename());
        String extension = extension(filename);
        if (extension == null) {
            throw invalid("仅支持 PDF 或 DOCX 文件");
        }

        String contentType;
        if ("pdf".equalsIgnoreCase(extension)) {
            if (!hasPdfSignature(bytes)) {
                throw invalid("文件格式无效");
            }
            contentType = PDF_CONTENT_TYPE;
        } else {
            if (!hasZipSignature(bytes) || !hasRequiredDocxEntries(bytes)) {
                throw invalid("文件格式无效");
            }
            contentType = DOCX_CONTENT_TYPE;
        }
        return new ValidatedFile(filename, contentType, bytes);
    }

    private byte[] readBytes(MultipartFile multipartFile) {
        try (InputStream input = multipartFile.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_FILE_SIZE + 1);
            if (bytes.length == 0) {
                throw invalid("上传文件不能为空");
            }
            if (bytes.length > MAX_FILE_SIZE) {
                throw invalid("文件大小不能超过5MiB");
            }
            return bytes;
        } catch (InvalidRequestException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalid("上传文件读取失败");
        }
    }

    static String safeFilename(String filename) {
        String value = filename == null ? "" : filename.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        value = slash >= 0 ? value.substring(slash + 1) : value;
        value = value.replaceAll("[\\p{Cntrl}]", "").trim();
        if (value.isEmpty()) {
            throw invalid("文件名无效");
        }
        if (value.length() <= 255) {
            return value;
        }
        int dot = value.lastIndexOf('.');
        String extension = dot > 0 ? value.substring(dot) : "";
        int prefixLength = Math.max(1, 255 - extension.length());
        return value.substring(0, prefixLength) + extension;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            return null;
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return "pdf".equals(extension) || "docx".equals(extension) ? extension : null;
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

    private static boolean hasRequiredDocxEntries(byte[] bytes) {
        Set<String> required = new HashSet<>(Set.of("[Content_Types].xml", "word/document.xml"));
        int entries = 0;
        long extractedBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) {
                    throw invalid("DOCX 文件条目数不能超过200");
                }
                if (entry.getName() != null) {
                    required.remove(entry.getName());
                }
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    extractedBytes += read;
                    if (extractedBytes > MAX_EXTRACTED_ZIP_BYTES) {
                        throw invalid("DOCX 解压后大小不能超过10MiB");
                    }
                }
                zip.closeEntry();
            }
            return required.isEmpty();
        } catch (InvalidRequestException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalid("文件格式无效");
        }
    }

    private static InvalidRequestException invalid(String message) {
        return new InvalidRequestException(message);
    }

    public record ValidatedFile(String originalFilename, String contentType, byte[] bytes) {
        public ValidatedFile {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
