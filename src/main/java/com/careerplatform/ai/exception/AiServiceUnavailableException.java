package com.careerplatform.ai.exception;

/** Raised when AI is disabled or no configured chat provider is available. */
public class AiServiceUnavailableException extends AiException {

    public AiServiceUnavailableException(String message) {
        super(message);
    }

    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
