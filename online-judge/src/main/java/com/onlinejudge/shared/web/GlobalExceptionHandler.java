package com.onlinejudge.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import com.onlinejudge.shared.security.AccessDeniedException;
import com.onlinejudge.shared.security.AuthenticationRequiredException;
import com.onlinejudge.execution.application.CodeRunLimitException;
import com.onlinejudge.aiquota.domain.QuotaExhaustedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import com.onlinejudge.system.application.TrialMetrics;

import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Autowired(required = false)
    private TrialMetrics trialMetrics;

    @ExceptionHandler(PlatformApiException.class)
    public ResponseEntity<Map<String, String>> handlePlatformApi(PlatformApiException exception,
                                                                 HttpServletRequest request) {
        log.warn("Platform request rejected. method={}, uri={}, code={}",
                request.getMethod(), request.getRequestURI(), exception.getCode());
        if (trialMetrics != null) {
            if ("ROSTER_MISMATCH".equals(exception.getCode())) trialMetrics.rosterMismatch();
            if ("FORBIDDEN".equals(exception.getCode()) || "ASSIGNMENT_NOT_TARGETED".equals(exception.getCode())) {
                trialMetrics.accessDenied();
            }
        }
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("code", exception.getCode(), "error", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException exception,
                                                                     HttpServletRequest request) {
        log.warn("Request rejected. method={}, uri={}, message={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException exception,
                                                                HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + "：" + error.getDefaultMessage())
                .findFirst()
                .orElse("请求参数校验失败");
        log.warn("Validation failed. method={}, uri={}, message={}",
                request.getMethod(),
                request.getRequestURI(),
                message);
        return ResponseEntity.badRequest()
                .body(Map.of("error", message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParam(MissingServletRequestParameterException exception,
                                                                  HttpServletRequest request) {
        log.warn("Missing parameter. method={}, uri={}, parameter={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getParameterName());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "缺少请求参数：" + exception.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                                  HttpServletRequest request) {
        log.warn("Parameter type mismatch. method={}, uri={}, parameter={}, value={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getName(),
                exception.getValue());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "参数类型不正确：" + exception.getName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableBody(HttpMessageNotReadableException exception,
                                                                    HttpServletRequest request) {
        log.warn("Unreadable request body. method={}, uri={}, message={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "请求体格式不正确，请检查 JSON 字段"));
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationRequired(AuthenticationRequiredException exception,
                                                                           HttpServletRequest request) {
        log.warn("Authentication required. method={}, uri={}, message={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("code", "AUTH_REQUIRED", "error", exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException exception,
                                                                  HttpServletRequest request) {
        log.warn("Access denied. method={}, uri={}, message={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("code", "FORBIDDEN", "error", exception.getMessage()));
    }

    @ExceptionHandler(CodeRunLimitException.class)
    public ResponseEntity<Map<String, String>> handleCodeRunLimit(CodeRunLimitException exception,
                                                                  HttpServletRequest request) {
        log.warn("Code run limited. method={}, uri={}, message={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("code", "FORBIDDEN", "error", exception.getMessage()));
    }

    @ExceptionHandler(QuotaExhaustedException.class)
    public ResponseEntity<Map<String, String>> handleQuotaExhausted(QuotaExhaustedException exception,
                                                                    HttpServletRequest request) {
        log.warn("AI quota exhausted. method={}, uri={}", request.getMethod(), request.getRequestURI());
        if (trialMetrics != null) trialMetrics.quotaRejected();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("code", "QUOTA_EXHAUSTED", "error", "本月课堂 AI 额度已用完，代码评测仍可正常使用"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoResourceFoundException exception,
                                                              HttpServletRequest request) {
        log.warn("Resource not found. method={}, uri={}",
                request.getMethod(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "资源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception exception,
                                                             HttpServletRequest request) {
        log.error("Unhandled server exception. method={}, uri={}",
                request.getMethod(),
                request.getRequestURI(),
                exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "服务器内部错误，请稍后重试"));
    }
}
