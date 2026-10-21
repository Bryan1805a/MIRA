package com.mira.mira_backend.exception;

import com.mira.mira_backend.dto.ApiErrorResponse;
import com.mira.mira_backend.security.RequestIdFilter;
import com.mira.mira_backend.service.DuplicateEmailException;
import com.mira.mira_backend.service.InvalidCredentialsException;
import com.mira.mira_backend.service.InvalidRegistrationException;
import com.mira.mira_backend.service.UserNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidRegistrationException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRegistration(
            InvalidRegistrationException exception,
            HttpServletRequest request
    ) {
        return error(
                request,
                HttpStatus.BAD_REQUEST,
                "invalid_registration",
                exception.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationFailure(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ApiErrorResponse.Detail> details =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(fieldError -> new ApiErrorResponse.Detail(
                                fieldError.getField(),
                                fieldError.getDefaultMessage() == null
                                        ? "is invalid"
                                        : fieldError.getDefaultMessage()
                        ))
                        .toList();

        return error(
                request,
                HttpStatus.BAD_REQUEST,
                "invalid_request",
                "Request fields are missing or invalid.",
                details
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(
            MissingRequestHeaderException exception,
            HttpServletRequest request
    ) {
        return error(
                request,
                HttpStatus.BAD_REQUEST,
                "invalid_request",
                "Required header '" + exception.getHeaderName() + "' is missing."
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return error(
                request,
                HttpStatus.BAD_REQUEST,
                "malformed_request",
                "The request body is missing or malformed."
        );
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(
            DuplicateEmailException exception,
            HttpServletRequest request
    ) {
        return error(
                request,
                HttpStatus.CONFLICT,
                "email_already_registered",
                exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ) {
        return error(
                request,
                HttpStatus.UNAUTHORIZED,
                "invalid_credentials",
                "Invalid email or password."
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(
            UserNotFoundException exception,
            HttpServletRequest request
    ) {
        return error(
                request,
                HttpStatus.NOT_FOUND,
                "resource_not_found",
                "The requested resource was not found."
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return error(
                request,
                HttpStatus.NOT_FOUND,
                "resource_not_found",
                "The requested resource was not found."
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "Unhandled request failure requestId={} exception={}",
                RequestIdFilter.requestId(request),
                exception.getClass().getName()
        );

        return error(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal_error",
                "An unexpected error occurred."
        );
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpServletRequest request,
            HttpStatus status,
            String code,
            String message
    ) {
        return error(request, status, code, message, List.of());
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpServletRequest request,
            HttpStatus status,
            String code,
            String message,
            List<ApiErrorResponse.Detail> details
    ) {
        return ResponseEntity.status(status).body(
                new ApiErrorResponse(
                        code,
                        message,
                        RequestIdFilter.requestId(request),
                        details
                )
        );
    }
}