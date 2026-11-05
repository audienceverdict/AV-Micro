package com.slokam.av.exception.handler;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.slokam.av.exception.ApiErrors;
import com.slokam.av.exception.custom.ApiException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrors> api(ApiException e, HttpServletRequest r) {
        if (e.status >= 500) log.error("API operation failed status={} code={}", e.status, e.code);
        else log.warn("API request rejected status={} code={}", e.status, e.code);
        return ResponseEntity.status(e.status)
                .body(ApiErrors.of(e.status, e.code, e.getMessage(), r.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrors> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
        log.warn("Request validation failed fieldErrorCount={}", e.getBindingResult().getFieldErrorCount());
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult()
                .getFieldErrors()
                .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(
                        new ApiErrors(
                                Instant.now(),
                                400,
                                "VALIDATION_ERROR",
                                "Request validation failed",
                                r.getRequestURI(),
                                fields));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrors> invalidParameter(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e, HttpServletRequest r) {
        String detail = "Invalid value. " + expectedType(e.getRequiredType());
        return badRequest(r, "VALIDATION_ERROR", "Invalid request parameter '" + e.getName() + "': " + detail,
                e.getName(), detail);
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    ResponseEntity<ApiErrors> missingParameter(org.springframework.web.bind.MissingServletRequestParameterException e, HttpServletRequest r) {
        return badRequest(r, "VALIDATION_ERROR", "Required request parameter '" + e.getParameterName() + "' is missing",
                e.getParameterName(), "Required parameter is missing");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrors> malformed(HttpMessageNotReadableException e, HttpServletRequest r) {
        log.warn("Request rejected: malformed body");
        JsonMappingException mappingFailure = null;
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof UnrecognizedPropertyException unknown) {
                return badRequest(r, "MALFORMED_REQUEST",
                        "Unsupported field '" + unknown.getPropertyName() + "' in request body",
                        fieldPath(unknown), "Unsupported field");
            }
            if (cause instanceof MismatchedInputException mismatch) {
                String field = fieldPath(mismatch);
                String detail = (mismatch instanceof InvalidFormatException ? "Invalid value. " : "Invalid JSON type. ")
                        + expectedType(mismatch.getTargetType());
                if (mismatch.getProcessor() instanceof JsonParser parser) {
                    String actual = jsonType(parser.currentToken());
                    // Jackson may clear its parser token before the MVC exception handler runs.
                    // Match only fixed token markers; never return the exception message or input.
                    if (actual == null && mismatch.getOriginalMessage() != null) {
                        if (mismatch.getOriginalMessage().contains("JsonToken.START_OBJECT")) actual = "an object";
                        else if (mismatch.getOriginalMessage().contains("JsonToken.START_ARRAY")) actual = "an array";
                    }
                    if (actual != null) detail += " Received " + actual + ".";
                }
                return badRequest(r, "MALFORMED_REQUEST", "Invalid field '" + field + "': " + detail, field, detail);
            }
            if (cause instanceof JsonParseException syntax) {
                String detail = "Malformed JSON syntax";
                if (syntax.getLocation() != null) {
                    detail += " at line " + syntax.getLocation().getLineNr()
                            + ", column " + syntax.getLocation().getColumnNr();
                }
                return badRequest(r, "MALFORMED_REQUEST", detail, "body", detail);
            }
            if (cause instanceof JsonMappingException mapping) {
                mappingFailure = mapping;
            }
        }
        if (mappingFailure != null) {
            String field = fieldPath(mappingFailure);
            // Never expose Jackson's raw message, Java class names or the request content.
            String detail = "Invalid value or conflicting aliases for this field";
            return badRequest(r, "MALFORMED_REQUEST", "Invalid field '" + field + "': " + detail, field, detail);
        }
        return badRequest(r, "MALFORMED_REQUEST", "Request body is required and must contain valid JSON",
                "body", "Request body is required and must contain valid JSON");
    }

    private static ResponseEntity<ApiErrors> badRequest(HttpServletRequest request, String code,
                                                       String message, String field, String detail) {
        return ResponseEntity.badRequest().body(new ApiErrors(Instant.now(), 400, code, message,
                request.getRequestURI(), Map.of(field, detail)));
    }

    private static String fieldPath(JsonMappingException exception) {
        StringBuilder field = new StringBuilder();
        for (var reference : exception.getPath()) {
            if (reference.getFieldName() != null) {
                if (!field.isEmpty()) field.append('.');
                field.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                field.append('[').append(reference.getIndex()).append(']');
            }
        }
        if (field.isEmpty() && exception instanceof UnrecognizedPropertyException unknown) {
            field.append(unknown.getPropertyName());
        }
        return field.isEmpty() ? "body" : field.toString();
    }

    private static String expectedType(Class<?> type) {
        if (type == null) return "Use the documented field format.";
        if (type.isEnum()) return "Expected one of: " + String.join(", ",
                Arrays.stream(type.getEnumConstants()).map(value -> ((Enum<?>) value).name()).toList()) + ".";
        if (type == String.class || type == Character.class || type == char.class) return "Expected a string.";
        if (type == java.time.LocalDate.class) return "Expected a valid date in YYYY-MM-DD format.";
        if (type == java.time.LocalDateTime.class) return "Expected an ISO-8601 date-time (e.g. 2026-10-31T18:30:00).";
        if (type == Instant.class || type == java.time.OffsetDateTime.class || type == java.time.ZonedDateTime.class)
            return "Expected an ISO-8601 timestamp with a timezone (e.g. 2026-10-31T18:30:00Z).";
        if (type == boolean.class || type == Boolean.class) return "Expected a boolean (true or false).";
        if (type == int.class || type == long.class || type == short.class || type == byte.class
                || type == Integer.class || type == Long.class || type == Short.class || type == Byte.class
                || type == java.math.BigInteger.class) return "Expected a whole number within the supported range.";
        if (Number.class.isAssignableFrom(type) || type == double.class || type == float.class) return "Expected a number.";
        if (type.isArray() || Collection.class.isAssignableFrom(type)) return "Expected an array.";
        return "Expected a JSON object with the documented fields.";
    }

    private static String jsonType(JsonToken token) {
        if (token == null) return null;
        return switch (token) {
            case START_ARRAY, END_ARRAY -> "an array";
            case START_OBJECT, END_OBJECT, FIELD_NAME -> "an object";
            case VALUE_STRING -> "a string";
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> "a number";
            case VALUE_TRUE, VALUE_FALSE -> "a boolean";
            case VALUE_NULL -> "null";
            default -> null;
        };
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrors> duplicate(HttpServletRequest r) {
        log.warn("Request rejected: data integrity conflict");
        if (r.getRequestURI().contains("/seat-holds"))
            return ResponseEntity.status(409)
                    .body(
                            ApiErrors.of(
                                    409,
                                    "SEAT_UNAVAILABLE",
                                    "One or more seats are currently unavailable",
                                    r.getRequestURI()));
        return ResponseEntity.status(409)
                .body(
                        ApiErrors.of(
                                409,
                                "DUPLICATE_VALUE",
                                r.getRequestURI().startsWith("/api/v1/movies") || r.getRequestURI().startsWith("/api/v1/people")
                                        ? "Catalog value conflicts with an existing record" : "Mobile or email is already in use",
                                r.getRequestURI()));
    }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<ApiErrors> forbidden(HttpServletRequest r) {
        log.warn("Request rejected: access denied");
        return ResponseEntity.status(403)
                .body(ApiErrors.of(403, "FORBIDDEN", "Access denied", r.getRequestURI()));
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    ResponseEntity<ApiErrors> unauthorized(HttpServletRequest r) {
        log.warn("Request rejected: authentication required");
        return ResponseEntity.status(401)
                .body(ApiErrors.of(401, "UNAUTHORIZED", "Authentication required", r.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrors> unexpected(Exception e, HttpServletRequest r) {
        if (e instanceof org.springframework.web.ErrorResponse response) {
            int status = response.getStatusCode().value();
            if (status >= 500) log.error("Framework request failure status={} failureType={}", status, e.getClass().getName());
            else log.warn("Framework request rejected status={} failureType={}", status, e.getClass().getName());
            var httpStatus = org.springframework.http.HttpStatus.resolve(status);
            return ResponseEntity.status(status).body(ApiErrors.of(status, "REQUEST_ERROR",
                    httpStatus == null ? "Request failed" : httpStatus.getReasonPhrase(), r.getRequestURI()));
        }
        // Exception messages can include SQL values, email content, or credentials.
        log.error("Unexpected request failure failureType={}", e.getClass().getName());
        for (StackTraceElement frame : e.getStackTrace()) {
            log.error("    at {}", frame);
        }
        return ResponseEntity.internalServerError()
                .body(ApiErrors.of(500, "INTERNAL_ERROR", "An unexpected error occurred", r.getRequestURI()));
    }
}

