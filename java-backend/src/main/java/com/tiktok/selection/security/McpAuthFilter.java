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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * MCP 内部端点共享 Token 认证过滤器
 *
 * <p>遵循阿里巴巴Java开发手册（黄山版）安全规约第1条：
 * 隶属于用户个人的页面或者功能必须进行权限控制校验。
 * /mcp/jsonrpc 虽为内部服务间调用，仍需验证调用方身份，
 * 防止未授权外部请求操控 block_chain 或读取内部状态。
 *
 * <p>认证方式：调用方（Python Agent）在请求头中携带预共享 Token：
 * {@code X-MCP-Token: <mcp.internal.secret-token>}
 * Token 通过环境变量注入，禁止硬编码。
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class McpAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpAuthFilter.class);

    /** Python Agent 调用时携带的认证 Token 请求头名称 */
    static final String MCP_TOKEN_HEADER = "X-MCP-Token";

    private static final String MCP_PATH      = "/mcp/jsonrpc";
    private static final String INTERNAL_PATH = "/api/internal/";

    @Value("${mcp.internal.secret-token}")
    private String mcpSecretToken;

    private final ObjectMapper objectMapper;

    public McpAuthFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader(MCP_TOKEN_HEADER);

        if (!isValidToken(token)) {
            log.warn("MCP unauthorized access: ip={}, uri={}, hasToken={}",
                    request.getRemoteAddr(), request.getRequestURI(), token != null);
            rejectUnauthorized(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 对 /mcp/jsonrpc 和 /api/internal/** 端点生效（Python AI 服务内部调用）
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !MCP_PATH.equals(uri) && !uri.startsWith(INTERNAL_PATH);
    }

    /**
     * 常量时间比较，防止时序攻击（timing attack）探测 Token 长度/内容
     */
    private boolean isValidToken(String token) {
        if (token == null || mcpSecretToken == null || mcpSecretToken.isBlank()) {
            return false;
        }
        // MessageDigest.isEqual 是常量时间比较
        return java.security.MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                mcpSecretToken.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void rejectUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        R<Void> result = R.fail(ErrorCode.ACCESS_UNAUTHORIZED, "MCP端点访问未授权");
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
