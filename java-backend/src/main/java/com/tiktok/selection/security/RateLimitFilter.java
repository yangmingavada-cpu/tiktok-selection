package com.tiktok.selection.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.common.ErrorCode;
import com.tiktok.selection.common.R;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 认证接口频率限制过滤器，防止暴力破解和接口滥刷
 * <p>
 * 使用Redis滑动窗口计数，对登录和注册接口按IP限流（安全规约第8条）
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** 登录接口：每IP每分钟最多10次 */
    private static final int LOGIN_MAX_ATTEMPTS = 10;
    /** 注册接口：每IP每分钟最多5次 */
    private static final int REGISTER_MAX_ATTEMPTS = 5;
    /** 滑动窗口大小（秒） */
    private static final long WINDOW_SECONDS = 60;

    private static final String RATE_LIMIT_PREFIX = "rate_limit:auth:";

    /** HTTP 429 Too Many Requests（HttpServletResponse 无此常量） */
    private static final int SC_TOO_MANY_REQUESTS = 429;

    /** 合法 IPv4 / IPv6 格式，防止 X-Forwarded-For 伪造绕过限流 */
    private static final Pattern IP_PATTERN =
            Pattern.compile("^((\\d{1,3}\\.){3}\\d{1,3}|[0-9a-fA-F:]{2,39})$");

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (!"POST".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        int maxAttempts;
        if (path.endsWith("/api/auth/login")) {
            maxAttempts = LOGIN_MAX_ATTEMPTS;
        } else if (path.endsWith("/api/auth/register")) {
            maxAttempts = REGISTER_MAX_ATTEMPTS;
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        String redisKey = RATE_LIMIT_PREFIX + path + ":" + clientIp;

        Long current = stringRedisTemplate.opsForValue().increment(redisKey);
        if (current != null && current == 1) {
            stringRedisTemplate.expire(redisKey, WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        if (current != null && current > maxAttempts) {
            log.warn("Rate limit exceeded: ip={}, path={}, attempts={}", clientIp, path, current);
            rejectRequest(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            String candidate = xff.split(",")[0].trim();
            if (IP_PATTERN.matcher(candidate).matches()) {
                return candidate;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            String candidate = realIp.trim();
            if (IP_PATTERN.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return request.getRemoteAddr();
    }

    private void rejectRequest(HttpServletResponse response) throws IOException {
        response.setStatus(SC_TOO_MANY_REQUESTS);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        R<Void> result = R.fail(ErrorCode.USER_ERROR, "请求过于频繁，请稍后再试");
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/auth/");
    }
}
