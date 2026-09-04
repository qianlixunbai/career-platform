package com.careerplatform.ai.exception;

/** Raised when the configured AI provider cannot complete a request. */
public class AiProviderException extends AiException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
