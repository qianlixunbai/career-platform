package com.careerplatform.common.exception;

import java.time.Instant;

public class ApiErrorResponse {

    private final String code;

    private final String message;

    private final Instant timestamp;

    public ApiErrorResponse(String code, String message, Instant timestamp) {
        this.code = code;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
