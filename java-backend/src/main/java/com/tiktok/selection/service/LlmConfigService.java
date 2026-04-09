package com.tiktok.selection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tiktok.selection.common.AesEncryptUtil;
import com.tiktok.selection.common.BusinessException;
import com.tiktok.selection.common.ErrorCode;
import com.tiktok.selection.dto.request.LlmConfigSaveRequest;
import com.tiktok.selection.dto.response.LlmConfigVO;
import com.tiktok.selection.entity.LlmConfig;
import com.tiktok.selection.mapper.LlmConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM配置服务
 * 读取当前激活的LLM配置，供IntentService调用Python时传递；
 * 提供管理员CRUD操作，写入时对apiKey进行AES-256-GCM加密存储。
 *
 * @author system
 * @date 2026/03/24
 */
@Service
public class LlmConfigService {

    private static final Logger log = LoggerFactory.getLogger(LlmConfigService.class);

    private final LlmConfigMapper llmConfigMapper;

    /**
     * 规约第10条：配置文件中的密码需要加密。
     * AES-256-GCM 密钥通过环境变量注入，用于加密/解密数据库中存储的 LLM API Key。
     * 生成32字节随机密钥：openssl rand -base64 32
     */
    @Value("${encrypt.aes.secret-key}")
    private String aesSecretKey;

    public LlmConfigService(LlmConfigMapper llmConfigMapper) {
        this.llmConfigMapper = llmConfigMapper;
    }

    // ==================== 读取（内部调用） ====================

    /**
     * 获取当前激活的LLM配置（优先级最高的active=true的配置）
     */
    public LlmConfig getActiveLlmConfig() {
        List<LlmConfig> configs = llmConfigMapper.selectList(
                new LambdaQueryWrapper<LlmConfig>()
                        .eq(LlmConfig::getActive, true)
                        .orderByAsc(LlmConfig::getPriority)
                        .last("LIMIT 1"));
        if (configs.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "未找到激活的LLM配置，请在管理后台添加LLM配置");
        }
        return configs.get(0);
    }

    /**
     * 将LlmConfig转为Python可用的配置Map（含解密后的明文API Key）
     */
    public Map<String, Object> toLlmConfigMap(LlmConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("base_url", config.getBaseUrl());
        // 规约第10条：apiKeyEncrypted 存储的是 AES-GCM 密文，此处解密后传递给 Python
        String decryptedApiKey = AesEncryptUtil.decrypt(config.getApiKeyEncrypted(), aesSecretKey);
        map.put("api_key", decryptedApiKey);
        map.put("model", config.getModel());
        map.put("max_tokens", config.getMaxTokens() != null ? config.getMaxTokens() : 4096);
        map.put("context_window", config.getContextWindow() != null ? config.getContextWindow() : 128000);
        map.put("compact_message_limit", config.getCompactMessageLimit() != null ? config.getCompactMessageLimit() : 48);
        map.put("compact_char_limit", config.getCompactCharLimit() != null ? config.getCompactCharLimit() : 18000);
        if (config.getConfigExtra() != null) {
            map.put("config_extra", config.getConfigExtra());
        }
        // 规约日志第6条：debug级别输出必须判断开关
        if (log.isDebugEnabled()) {
            log.debug("LLM config loaded: provider={}, model={}", config.getProvider(), config.getModel());
        }
        return map;
    }

    /**
     * 按ID获取LLM配置实体（内部使用，含加密字段）
     */
    public LlmConfig getById(String id) {
        LlmConfig config = llmConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "LLM配置不存在");
        }
        return config;
    }

    /**
     * 获取所有激活的LLM配置Map列表（按优先级排序），用于 fallback 机制。
     * 返回解密后的配置Map列表，供Python端按顺序尝试。
     */
    public List<Map<String, Object>> getActiveLlmConfigMaps() {
        List<LlmConfig> configs = llmConfigMapper.selectList(
                new LambdaQueryWrapper<LlmConfig>()
                        .eq(LlmConfig::getActive, true)
                        .orderByAsc(LlmConfig::getPriority));
        if (configs.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "未找到激活的LLM配置，请在管理后台添加LLM配置");
        }
        return configs.stream().map(this::toLlmConfigMap).toList();
    }

    /**
     * 查询所有LLM配置（返回原始实体，内部使用）
     */
    public List<LlmConfig> listAll() {
        return llmConfigMapper.selectList(
                new LambdaQueryWrapper<LlmConfig>()
                        .orderByAsc(LlmConfig::getPriority));
    }

    // ==================== 管理员CRUD ====================

    /**
     * 查询所有LLM配置，返回脱敏VO列表
     */
    public List<LlmConfigVO> listAllVO() {
        return listAll().stream().map(this::toVO).toList();
    }

    /**
     * 按ID查询，返回脱敏VO
     */
    public LlmConfigVO getVO(String id) {
        LlmConfig config = llmConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "LLM配置不存在");
        }
        return toVO(config);
    }

    /**
     * 创建LLM配置。
     * apiKey 明文由调用方传入，服务层加密后存库。
     */
    public LlmConfigVO create(LlmConfigSaveRequest req) {
        if (!StringUtils.hasText(req.getApiKey())) {
            throw new BusinessException(ErrorCode.REQUIRED_PARAM_EMPTY, "创建配置时apiKey不能为空");
        }
        LlmConfig config = new LlmConfig();
        fillFields(config, req);
        // 加密存储 API Key
        config.setApiKeyEncrypted(AesEncryptUtil.encrypt(req.getApiKey(), aesSecretKey));
        llmConfigMapper.insert(config);
        log.info("LLM config created: id={}, provider={}, model={}", config.getId(), config.getProvider(), config.getModel());
        return toVO(config);
    }

    /**
     * 更新LLM配置。
     * apiKey 为空时保留原有加密值；不为空时重新加密存储。
     */
    public LlmConfigVO update(String id, LlmConfigSaveRequest req) {
        LlmConfig config = llmConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "LLM配置不存在");
        }
        fillFields(config, req);
        // 仅在显式传入新Key时才重新加密
        if (StringUtils.hasText(req.getApiKey())) {
            config.setApiKeyEncrypted(AesEncryptUtil.encrypt(req.getApiKey(), aesSecretKey));
            log.info("LLM config apiKey rotated: id={}", id);
        }
        llmConfigMapper.updateById(config);
        log.info("LLM config updated: id={}, provider={}, model={}", id, config.getProvider(), config.getModel());
        return toVO(config);
    }

    /**
     * 删除LLM配置（物理删除，llm_config 表无逻辑删除字段）
     */
    public void delete(String id) {
        LlmConfig config = llmConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "LLM配置不存在");
        }
        llmConfigMapper.deleteById(id);
        log.info("LLM config deleted: id={}, name={}", id, config.getName());
    }

    // ==================== 私有工具 ====================

    /** configExtra 最大允许键数，防止超大 Map 导致 OOM（规约第4条：P2-3修复） */
    private static final int CONFIG_EXTRA_MAX_KEYS = 20;

    /**
     * 将请求字段填充到实体（apiKeyEncrypted 由调用方单独处理）
     */
    private void fillFields(LlmConfig config, LlmConfigSaveRequest req) {
        config.setName(req.getName());
        config.setProvider(req.getProvider());
        config.setBaseUrl(req.getBaseUrl());
        config.setModel(req.getModel());
        if (req.getMaxTokens() != null) {
            config.setMaxTokens(req.getMaxTokens());
        }
        if (req.getActive() != null) {
            config.setActive(req.getActive());
        }
        if (req.getPriority() != null) {
            config.setPriority(req.getPriority());
        }
        if (req.getMonthlyTokenLimit() != null) {
            config.setMonthlyTokenLimit(req.getMonthlyTokenLimit());
        }
        // P2-3修复：限制configExtra键数量，防止超大Map占用内存（规约第4条）
        if (req.getConfigExtra() != null && req.getConfigExtra().size() > CONFIG_EXTRA_MAX_KEYS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "configExtra最多允许" + CONFIG_EXTRA_MAX_KEYS + "个配置项");
        }
        config.setConfigExtra(req.getConfigExtra());
    }

    /**
     * 实体转VO，API Key脱敏（解密后取后4位，格式 ****xxxx）。
     * 若解密失败（如环境变量未配置），显示 "****????" 而非抛出异常。
     */
    LlmConfigVO toVO(LlmConfig config) {
        String masked = maskApiKey(config.getApiKeyEncrypted());
        return LlmConfigVO.builder()
                .id(config.getId())
                .name(config.getName())
                .provider(config.getProvider())
                .baseUrl(config.getBaseUrl())
                .apiKeyMasked(masked)
                .model(config.getModel())
                .maxTokens(config.getMaxTokens())
                .active(config.getActive())
                .priority(config.getPriority())
                .monthlyTokenLimit(config.getMonthlyTokenLimit())
                .monthlyTokensUsed(config.getMonthlyTokensUsed())
                .configExtra(config.getConfigExtra())
                .createTime(config.getCreateTime())
                .updateTime(config.getUpdateTime())
                .build();
    }

    private String maskApiKey(String encryptedKey) {
        if (!StringUtils.hasText(encryptedKey)) {
            return "****（未设置）";
        }
        try {
            String plainKey = AesEncryptUtil.decrypt(encryptedKey, aesSecretKey);
            if (plainKey.length() <= 4) {
                return "****";
            }
            return "****" + plainKey.substring(plainKey.length() - 4);
        } catch (Exception e) {
            log.warn("API Key解密失败，可能是AES_SECRET_KEY不匹配或数据尚未迁移: {}", e.getMessage());
            return "****????";
        }
    }
}
