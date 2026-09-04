package com.agentcrawler.api;

import com.agentcrawler.core.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, Object>> handleAppException(AppException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ex.getCode().name());
        body.put("message", ex.getMessage());
        HttpStatus status = switch (ex.getCode()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case CONVERSATION_NOT_FOUND -> HttpStatus.BAD_REQUEST;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(body);
    }
}

@RestController
class HealthController {
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
