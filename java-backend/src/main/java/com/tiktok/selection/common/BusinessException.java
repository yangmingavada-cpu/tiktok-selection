package com.tiktok.selection.common;

/**
 * 业务异常，用于表示可预见的业务逻辑错误
 *
 * @author system
 * @date 2026/03/22
 */
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String message) {
        super(message);
        this.code = ErrorCode.PARAM_ERROR;
    }

    public BusinessException(String errorCode, String message) {
        super(message);
        this.code = errorCode;
    }

    public BusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.code = errorCode;
    }

    public String getCode() {
        return code;
    }
}
