package com.tiktok.selection.common;

import lombok.Data;

/**
 * 统一响应包装类
 *
 * @author system
 * @date 2026/03/22
 */
@Data
public class R<T> {

    private String code;
    private String message;
    private T data;

    private R() {}

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = ErrorCode.SUCCESS;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(String errorCode, String message) {
        R<T> r = new R<>();
        r.code = errorCode;
        r.message = message;
        return r;
    }

    public static <T> R<T> fail(String message) {
        return fail(ErrorCode.SYSTEM_ERROR, message);
    }
}
