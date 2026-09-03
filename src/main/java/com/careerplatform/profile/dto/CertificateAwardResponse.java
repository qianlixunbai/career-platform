package com.careerplatform.profile.dto;

import com.careerplatform.profile.enums.CertificateAwardType;
import java.time.LocalDate;

public record CertificateAwardResponse(Long id, String name, CertificateAwardType type, String issuer,
                                       LocalDate issueDate, String description) {
}
