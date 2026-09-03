package com.careerplatform.career.dto;

import com.careerplatform.career.enums.JobType;
import com.careerplatform.career.enums.SourceType;
import java.time.LocalDate;

public record JobResponse(Long id, Long companyId, String title, String city, JobType jobType,
                          LocalDate publishDate, LocalDate deadline, String rawJd, SourceType sourceType,
                          String sourceName, String sourceUrl, Boolean archived) { }
