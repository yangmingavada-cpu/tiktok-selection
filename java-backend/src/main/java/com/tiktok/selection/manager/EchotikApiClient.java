package com.tiktok.selection.manager;

import com.tiktok.selection.common.BusinessException;
import com.tiktok.selection.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Echotik第三方API客户端，封装HTTP调用、异常转换与结果适配
 * <p>
 * 认证方式：HTTP Basic Auth（username=apiKey, password=apiSecret）
 * 响应格式：{code, message, data, requestId}，本客户端自动提取 data 字段
 *
 * @author system
 * @date 2026/03/22
 */
@Component
public class EchotikApiClient {

    private static final Logger log = LoggerFactory.getLogger(EchotikApiClient.class);

    private static final int REQUEST_TIMEOUT_SECONDS = 30;
    private static final int CONNECT_TIMEOUT_MILLIS = 10000;

    /** Echotik API 响应成功码 */
    private static final int ECHOTIK_SUCCESS_CODE = 0;

    @Value("${echotik.api.base-url:}")
    private String baseUrl;

    private final WebClient webClient;

    public EchotikApiClient(WebClient.Builder webClientBuilder) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS);
        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * 发送 GET 请求并返回完整响应 Map（含 code/message/data/requestId）
     *
     * @param endpoint API路径（如 "echotik/product/list"）
     * @param params   查询参数
     * @param username Echotik Basic Auth 用户名（即 apiKey）
     * @param password Echotik Basic Auth 密码（即 apiSecret）
     */
    @SuppressWarnings("java:S2139") // log + rethrow-with-cause is intentional: preserves stack in logs while propagating context
    public Map<String, Object> request(String endpoint,
                                       Map<String, Object> params,
                                       String username,
                                       String password) {
        validateBaseUrl();
        String url = buildUrl(endpoint);
        log.info("Echotik API request: GET {}, params={}", url, params != null ? params.size() : 0);

        Map<String, Object> response;
        try {
            response = webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.scheme(extractScheme(url))
                                .host(extractHost(url))
                                .path(extractPath(url));
                        if (params != null) {
                            params.forEach((key, value) -> {
                                if (value != null) {
                                    uriBuilder.queryParam(key, value.toString());
                                }
                            });
                        }
                        return uriBuilder.build();
                    })
                    .headers(h -> h.setBasicAuth(username, password))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS));
        } catch (WebClientResponseException e) {
            log.error("Echotik API HTTP error: endpoint={}, status={}", endpoint, e.getStatusCode(), e);
            throw new BusinessException(ErrorCode.THIRD_PARTY_ERROR,
                    "Echotik API调用失败: " + endpoint + ", HTTP " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Echotik API request failed: endpoint={}, error={}", endpoint, e.getMessage(), e);
            throw new BusinessException(ErrorCode.THIRD_PARTY_ERROR,
                    "Echotik API调用失败: " + endpoint, e);
        }

        if (response == null) {
            return Collections.emptyMap();
        }
        // checkResponseCode 抛出的 BusinessException 在 try-catch 之外，直接传播
        checkResponseCode(response, endpoint);
        log.info("Echotik API response OK: endpoint={}", endpoint);
        return response;
    }

    /**
     * 发送 GET 请求并自动提取响应中的 data 数组。
     * Echotik 响应格式：{code: 0, message: "ok", data: [...], requestId: "..."}
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> requestList(String endpoint,
                                                  Map<String, Object> params,
                                                  String username,
                                                  String password) {
        Map<String, Object> response = request(endpoint, params, username, password);
        Object data = response.get("data");
        if (data instanceof List<?> list) {
            log.info("Echotik API list OK: endpoint={}, items={}", endpoint, list.size());
            return (List<Map<String, Object>>) list;
        }
        log.warn("Echotik API response missing 'data' list: endpoint={}", endpoint);
        return Collections.emptyList();
    }

    // ─── 私有工具方法 ──────────────────────────────────────────────────────────────

    private String buildUrl(String endpoint) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String ep = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        return base + ep;
    }

    /**
     * 检查 Echotik 业务响应码，非0时抛出业务异常
     */
    private void checkResponseCode(Map<String, Object> response, String endpoint) {
        Object codeObj = response.get("code");
        if (codeObj == null) {
            return;
        }
        int code = ((Number) codeObj).intValue();
        if (code != ECHOTIK_SUCCESS_CODE) {
            String message = response.getOrDefault("message", "Echotik API业务错误").toString();
            log.error("Echotik API business error: endpoint={}, code={}, message={}", endpoint, code, message);
            throw new BusinessException(ErrorCode.THIRD_PARTY_ERROR,
                    "Echotik API业务错误: code=" + code + ", " + message);
        }
    }

    private void validateBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Echotik API base URL未配置");
        }
        if (!baseUrl.startsWith("https://") && !baseUrl.startsWith("http://")) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Echotik API base URL必须使用 http/https");
        }
    }

    private String extractScheme(String url) {
        int idx = url.indexOf("://");
        return idx > 0 ? url.substring(0, idx) : "https";
    }

    private String extractHost(String url) {
        int start = url.indexOf("://");
        if (start < 0) { start = 0; } else { start += 3; }
        int end = url.indexOf('/', start);
        if (end < 0) { end = url.length(); }
        String hostPort = url.substring(start, end);
        int colonIdx = hostPort.lastIndexOf(':');
        return colonIdx > 0 ? hostPort.substring(0, colonIdx) : hostPort;
    }

    private String extractPath(String url) {
        int start = url.indexOf("://");
        if (start < 0) { start = 0; } else { start += 3; }
        int pathStart = url.indexOf('/', start);
        return pathStart < 0 ? "/" : url.substring(pathStart);
    }
}
