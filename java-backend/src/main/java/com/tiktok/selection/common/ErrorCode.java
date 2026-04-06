package com.tiktok.selection.common;

/**
 * 统一错误码常量，遵循阿里巴巴Java开发手册错误码规范
 * 格式：错误来源(A/B/C) + 四位数字编号
 *
 * @author system
 * @date 2026/03/22
 */
public final class ErrorCode {

    private ErrorCode() {}

    /** 成功 */
    public static final String SUCCESS = "00000";

    // ==================== A: 用户端错误 ====================

    /** 用户端错误（一级宏观） */
    public static final String USER_ERROR = "A0001";

    /** 用户名已存在 */
    public static final String USER_NAME_ALREADY_EXISTS = "A0111";

    /** 用户登录异常（二级宏观） */
    public static final String USER_LOGIN_ERROR = "A0200";

    /** 用户账户不存在 */
    public static final String USER_ACCOUNT_NOT_EXIST = "A0201";

    /** 用户账户被冻结 */
    public static final String USER_ACCOUNT_FROZEN = "A0202";

    /** 用户密码错误 */
    public static final String USER_PASSWORD_ERROR = "A0210";

    /** 访问未授权 */
    public static final String ACCESS_UNAUTHORIZED = "A0301";

    /** 用户请求参数错误（二级宏观） */
    public static final String PARAM_ERROR = "A0400";

    /** 无效的用户输入 */
    public static final String INVALID_USER_INPUT = "A0402";

    /** 请求必填参数为空 */
    public static final String REQUIRED_PARAM_EMPTY = "A0410";

    /** 用户配额已用光 */
    public static final String QUOTA_EXHAUSTED = "A0605";

    /** 请求次数超出限制（防刷，规约第11条） */
    public static final String RATE_LIMIT_EXCEEDED = "A0501";

    // ==================== B: 系统执行出错 ====================

    /** 系统执行出错（一级宏观） */
    public static final String SYSTEM_ERROR = "B0001";

    /** 系统执行超时 */
    public static final String SYSTEM_TIMEOUT = "B0100";

    /** 系统资源异常 */
    public static final String SYSTEM_RESOURCE_ERROR = "B0300";

    // ==================== C: 调用第三方服务出错 ====================

    /** 调用第三方服务出错（一级宏观） */
    public static final String THIRD_PARTY_ERROR = "C0001";

    /** RPC服务出错 */
    public static final String RPC_SERVICE_ERROR = "C0110";
}
