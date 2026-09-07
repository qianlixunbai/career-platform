package com.careerplatform.common.exception;

import com.careerplatform.ai.exception.AiInvalidResponseException;
import com.careerplatform.ai.exception.AiProviderException;
import com.careerplatform.ai.exception.AiServiceUnavailableException;
import com.careerplatform.auth.UnauthorizedException;
import com.careerplatform.user.exception.InvalidCredentialsException;
import com.careerplatform.user.exception.UsernameAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadTooLarge() {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "文件不能超过5 MiB");
    }

    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidMultipart() {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "文件上传请求无效");
    }

    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingUpload() {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请选择上传文件");
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleAiServiceUnavailable(AiServiceUnavailableException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "AI_SERVICE_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleAiProviderUnavailable(AiProviderException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "AI_PROVIDER_UNAVAILABLE", "AI Provider 当前不可用，请稍后重试");
    }

    @ExceptionHandler(AiInvalidResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleAiInvalidResponse(AiInvalidResponseException exception) {
        if (exception.getRuleId() != null) {
            log.warn("AI_INVALID_RESPONSE stage={} rule={}", exception.getStage(), exception.getRuleId());
        }
        return error(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE", "AI 返回内容无法安全处理，请重试");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateResource(DuplicateResourceException exception) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", exception.getMessage());
    }

    @ExceptionHandler(ResourceInUseException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceInUse(ResourceInUseException exception) {
        return error(HttpStatus.CONFLICT, "RESOURCE_IN_USE", exception.getMessage());
    }

    @ExceptionHandler(InvalidResourceStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidResourceState(InvalidResourceStateException exception) {
        return error(HttpStatus.CONFLICT, "INVALID_RESOURCE_STATE", exception.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(InvalidRequestException exception) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "INVALID_CREDENTIALS",
                exception.getMessage(),
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(
            UnauthorizedException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "UNAUTHORIZED",
                exception.getMessage(),
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleUsernameAlreadyExists(
            UsernameAlreadyExistsException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "USERNAME_ALREADY_EXISTS",
                exception.getMessage(),
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().getFirst();
        String message = fieldError.getField() + ": " + fieldError.getDefaultMessage();
        ApiErrorResponse response = new ApiErrorResponse(
                "VALIDATION_ERROR",
                message,
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求格式无效");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求参数格式无效");
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        ApiErrorResponse response = new ApiErrorResponse(code, message, Instant.now());
        return ResponseEntity.status(status).body(response);
    }
}
