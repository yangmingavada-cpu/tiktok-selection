package com.tiktok.selection.common;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Block执行安全校验工具类
 * 统一处理 Prompt Injection、XSS、字段覆盖、参数范围等安全风险
 * 遵循阿里巴巴Java开发手册（黄山版）四、安全规约
 *
 * @author system
 * @date 2026/03/24
 */
public final class BlockSecurityUtil {

    private BlockSecurityUtil() {}

    /** 允许的语言代码白名单（规约第4条：参数白名单限定） */
    private static final Set<String> ALLOWED_LANGUAGES = Set.of(
            "zh", "en", "ja", "ko", "fr", "de", "es", "pt", "ar", "th", "vi", "id", "ms"
    );

    /**
     * 禁止作为 output_field 的保留字段名（规约第4条：防止覆盖安全敏感字段）
     * 攻击者可将 output_field 设为 userId/sessionId 等字段名，静默篡改数据
     */
    private static final Set<String> RESERVED_FIELDS = Set.of(
            "id", "userId", "user_id", "sessionId", "session_id",
            "create_time", "update_time", "status", "password",
            "token", "secret", "api_key", "api_secret",
            "echotik_key_id", "echotikKeyId"
    );

    /** output_field 合法格式：字母开头，仅允许字母/数字/下划线，最长64字符 */
    private static final Pattern FIELD_NAME_PATTERN =
            Pattern.compile("^[a-zA-Z][\\w]{0,63}$");

    /**
     * 保留字段名的全小写集合，用于大小写不敏感校验。
     * 防止攻击者用 "UserId"、"PASSWORD" 等变体绕过保留字段检查。
     */
    private static final Set<String> RESERVED_FIELDS_LOWER = RESERVED_FIELDS.stream()
            .map(String::toLowerCase)
            .collect(Collectors.toUnmodifiableSet());

    /** HTML标签正则，用于XSS过滤（规约第5条） */
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");

    /** semantic_prompt 最大长度：防止 ReDoS 和资源耗尽（规约第4条） */
    public static final int MAX_PROMPT_LENGTH = 500;

    /** 用户可见消息文本最大长度（pause_message / error_message） */
    public static final int MAX_MESSAGE_LENGTH = 200;

    /** LLM返回内容单字段最大长度（ai_comment / reason） */
    public static final int MAX_CONTENT_LENGTH = 1000;

    /** inputData 最大行数，防止 OOM（规约第4条：page size过大导致内存溢出） */
    public static final int MAX_INPUT_ROWS = 5000;

    /** max_score 合法范围 */
    public static final int MIN_SCORE = 1;
    public static final int MAX_SCORE_LIMIT = 1000;

    /** max_chars 合法范围 */
    public static final int MIN_CHARS = 10;
    public static final int MAX_CHARS_LIMIT = 500;

    /**
     * 校验并返回合法的 output_field 名称
     * 规约第4条：用户传入的任何参数必须做有效性验证
     *
     * @param field        来自 blockConfig 的字段名
     * @param defaultField 默认字段名（已经过硬编码校验）
     * @return 合法字段名
     * @throws BusinessException 字段名非法或为保留字段时抛出
     */
    public static String validateOutputField(String field, String defaultField) {
        if (field == null || field.isBlank()) {
            return defaultField;
        }
        if (!FIELD_NAME_PATTERN.matcher(field).matches()) {
            throw new BusinessException(ErrorCode.INVALID_USER_INPUT,
                    "output_field格式非法，仅允许字母开头的字母/数字/下划线组合: " + field);
        }
        // 大小写不敏感校验，防止 "UserId"、"PASSWORD" 等变体绕过
        if (RESERVED_FIELDS_LOWER.contains(field.toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_USER_INPUT,
                    "output_field不允许使用保留字段名: " + field);
        }
        return field;
    }

    /**
     * 校验语言代码是否在白名单内
     * 规约第4条：METADATA字段值限定
     *
     * @param language 语言代码
     * @return 合法的小写语言代码
     * @throws BusinessException 不支持的语言代码时抛出
     */
    public static String validateLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "zh";
        }
        String lang = language.toLowerCase().trim();
        if (!ALLOWED_LANGUAGES.contains(lang)) {
            throw new BusinessException(ErrorCode.INVALID_USER_INPUT,
                    "不支持的语言代码: " + language);
        }
        return lang;
    }

    /**
     * 校验语义提示词（semantic_prompt）长度
     * 规约第4条：防止正则输入源串拒绝服务（ReDoS）及资源耗尽
     *
     * @param prompt        提示词
     * @param defaultPrompt 默认提示词
     * @return 校验通过的提示词
     * @throws BusinessException 超长时抛出
     */
    public static String validatePrompt(String prompt, String defaultPrompt) {
        if (prompt == null || prompt.isBlank()) {
            return defaultPrompt;
        }
        if (prompt.length() > MAX_PROMPT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_USER_INPUT,
                    "semantic_prompt长度超过限制，最多" + MAX_PROMPT_LENGTH + "字符，实际: " + prompt.length());
        }
        return prompt;
    }

    /**
     * 校验 max_score 范围
     * 规约第4条：参数格式不匹配/数量超出限制（A0425）
     *
     * @throws BusinessException 超出范围时抛出
     */
    public static int validateMaxScore(int value) {
        if (value < MIN_SCORE || value > MAX_SCORE_LIMIT) {
            throw new BusinessException(ErrorCode.INVALID_USER_INPUT,
                    "max_score超出允许范围[" + MIN_SCORE + ", " + MAX_SCORE_LIMIT + "]，实际: " + value);
        }
        return value;
    }

    /**
     * 校验 max_chars 范围
     *
     * @throws BusinessException 超出范围时抛出
     */
    public static int validateMaxChars(int value) {
        if (value < MIN_CHARS || value > MAX_CHARS_LIMIT) {
            throw new BusinessException(ErrorCode.INVALID_USER_INPUT,
                    "max_chars超出允许范围[" + MIN_CHARS + ", " + MAX_CHARS_LIMIT + "]，实际: " + value);
        }
        return value;
    }

    /**
     * 校验 inputData 行数上限，防止 OOM
     * 规约第4条：page size过大导致内存溢出
     *
     * @throws BusinessException 超过上限时抛出
     */
    public static void validateInputSize(int size) {
        if (size > MAX_INPUT_ROWS) {
            throw new BusinessException(ErrorCode.INVALID_USER_INPUT,
                    "输入数据量超过最大限制" + MAX_INPUT_ROWS + "条，实际: " + size);
        }
    }

    /**
     * 过滤内容中的 HTML 标签并截断到最大长度
     * 规约第5条：禁止向 HTML 页面输出未经安全过滤或未正确转义的用户数据
     * 用于 LLM 返回的 ai_comment、reason 等字段
     *
     * @param content   原始内容（来自外部服务，不可信）
     * @param maxLength 最大长度限制
     * @return 过滤后的安全字符串
     */
    public static String sanitizeContent(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        String sanitized = HTML_TAG_PATTERN.matcher(content).replaceAll("");
        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
        }
        return sanitized;
    }

    /**
     * 对用户可见的消息文本进行安全处理（过滤HTML、限制长度）
     * 用于 pause_message、SSE 推送错误描述等场景
     *
     * @param message        原始消息（来自 blockConfig，属于用户输入）
     * @param defaultMessage 默认消息
     * @return 处理后的安全消息
     */
    public static String sanitizeMessage(String message, String defaultMessage) {
        if (message == null || message.isBlank()) {
            return defaultMessage;
        }
        return sanitizeContent(message, MAX_MESSAGE_LENGTH);
    }
}
