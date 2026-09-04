package com.careerplatform.ai.exception;

/** Raised when a provider response cannot be parsed as the requested type. */
public class AiInvalidResponseException extends AiException {

    public AiInvalidResponseException(String message) {
        super(message);
    }

    public AiInvalidResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
