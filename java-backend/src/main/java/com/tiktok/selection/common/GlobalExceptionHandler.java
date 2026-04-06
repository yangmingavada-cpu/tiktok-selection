package com.tiktok.selection.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * @author system
 * @date 2026/03/22
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBusinessException(BusinessException e) {
        // 规约日志第12条：用warn记录用户输入错误（A类错误码）
        // 规约日志第9条：B类系统错误需包含案发现场+堆栈信息，使用error级别
        if (e.getCode() != null && e.getCode().startsWith("B")) {
            log.error("System error: code={}, {}", e.getCode(), e.getMessage(), e);
        } else {
            log.warn("Business exception: code={}, {}", e.getCode(), e.getMessage());
        }
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException e) {
        // SSE连接超时属于正常生命周期结束，不写response body，防止与text/event-stream类型冲突
        log.debug("Async request timeout (SSE connection closed): {}", e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleRuntimeException(RuntimeException e) {
        log.error("Runtime exception: {}", e.getMessage(), e);
        return R.fail(ErrorCode.SYSTEM_ERROR, "系统内部错误");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return R.fail(ErrorCode.PARAM_ERROR, message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return R.fail(ErrorCode.SYSTEM_ERROR, "服务器内部错误");
    }
}
