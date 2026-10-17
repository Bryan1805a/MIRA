
package com.mira.mira_backend.exception;

import com.mira.mira_backend.dto.ApiErrorResponse;
import com.mira.mira_backend.service.DuplicateEmailException;
import com.mira.mira_backend.service.InvalidRegistrationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidRegistrationException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRegistration(
            InvalidRegistrationException exception
    ) {
        return ResponseEntity.badRequest().body(
                new ApiErrorResponse(
                        "invalid_registration",
                        exception.getMessage()
                )
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationFailure(
            MethodArgumentNotValidException exception
    ) {
        return ResponseEntity.badRequest().body(
                new ApiErrorResponse(
                        "invalid_request",
                        "Request fields are missing or invalid."
                )
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(
            HttpMessageNotReadableException exception
    ) {
        return ResponseEntity.badRequest().body(
                new ApiErrorResponse(
                        "malformed_request",
                        "The request body is missing or malformed."
                )
        );
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(
            DuplicateEmailException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ApiErrorResponse(
                        "email_already_registered",
                        exception.getMessage()
                )
        );
    }
}
