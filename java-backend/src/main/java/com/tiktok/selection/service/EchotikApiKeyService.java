package com.tiktok.selection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tiktok.selection.common.AesEncryptUtil;
import com.tiktok.selection.common.BusinessException;
import com.tiktok.selection.common.ErrorCode;
import com.tiktok.selection.dto.request.EchotikApiKeySaveRequest;
import com.tiktok.selection.dto.response.EchotikApiKeyVO;
import com.tiktok.selection.entity.EchotikApiKey;
import com.tiktok.selection.mapper.EchotikApiKeyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Echotik API 密钥服务
 *
 * <p>密钥明文通过环境变量注入的 AES-256-GCM 密钥加密存储，
 * 查询时返回脱敏 VO（后4位可见）。
 * 密钥轮转（rotation）时支持仅更新 apiKey/apiSecret 而不影响其他配置项。
 *
 * @author system
 * @date 2026/03/24
 */
@Service
public class EchotikApiKeyService extends ServiceImpl<EchotikApiKeyMapper, EchotikApiKey> {

    private static final Logger logger = LoggerFactory.getLogger(EchotikApiKeyService.class);

    /** API Key 脱敏显示后缀长度 */
    private static final int MASK_SUFFIX_LEN = 4;

    @Value("${encrypt.aes.secret-key}")
    private String aesSecretKey;

    // ==================== 内部调用（引擎层） ====================

    /**
     * 获取当前可用的（active=true且remainingCalls>0）API密钥，优先选择剩余次数最多的。
     * 引擎层在执行每个 Block 前调用此方法获取密钥。
     */
    public Optional<EchotikApiKey> getAvailableKey() {
        List<EchotikApiKey> keys = list(new LambdaQueryWrapper<EchotikApiKey>()
                .eq(EchotikApiKey::getActive, true)
                .gt(EchotikApiKey::getRemainingCalls, 0)
                .orderByDesc(EchotikApiKey::getRemainingCalls)
                .last("LIMIT 1"));
        return keys.isEmpty() ? Optional.empty() : Optional.of(keys.get(0));
    }

    /**
     * 原子扣减指定密钥的余量次数（并发安全），同时更新最后使用时间。
     * 调用方传入实际消耗的次数；余量不足时自动扣至 0，不会出现负值。
     *
     * @param keyId 密钥 ID
     * @param calls 本次消耗次数（通常为 1）
     */
    public void decrementRemainingCalls(String keyId, int calls) {
        if (keyId == null || calls <= 0) {
            return;
        }
        int updated = getBaseMapper().decrementRemainingCalls(keyId, calls);
        if (updated == 0) {
            logger.warn("Echotik key decrement skipped (key inactive or not found): keyId={}", keyId);
        } else {
            logger.debug("Echotik key remaining_calls decremented by {}: keyId={}", calls, keyId);
        }
    }

    /**
     * 解密并返回指定密钥的 apiKey 明文（供引擎层调用API时使用）
     */
    public String decryptApiKey(EchotikApiKey key) {
        return AesEncryptUtil.decrypt(key.getApiKeyEncrypted(), aesSecretKey);
    }

    /**
     * 解密并返回指定密钥的 apiSecret 明文
     */
    public String decryptApiSecret(EchotikApiKey key) {
        return AesEncryptUtil.decrypt(key.getApiSecretEncrypted(), aesSecretKey);
    }

    // ==================== 管理员 CRUD ====================

    /**
     * 查询所有密钥，返回脱敏 VO 列表（按创建时间降序）
     */
    public List<EchotikApiKeyVO> listAllVO() {
        List<EchotikApiKey> keys = list(new LambdaQueryWrapper<EchotikApiKey>()
                .orderByDesc(EchotikApiKey::getCreateTime));
        return keys.stream().map(this::toVO).toList();
    }

    /**
     * 按 ID 查询，返回脱敏 VO
     */
    public EchotikApiKeyVO getVO(String id) {
        EchotikApiKey key = getById(id);
        if (key == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "API密钥不存在");
        }
        return toVO(key);
    }

    /**
     * 创建 API 密钥。
     * apiKey 和 apiSecret 明文由调用方传入，服务层加密后存库。
     */
    public EchotikApiKeyVO create(EchotikApiKeySaveRequest req) {
        if (!StringUtils.hasText(req.getApiKey())) {
            throw new BusinessException(ErrorCode.REQUIRED_PARAM_EMPTY, "创建密钥时apiKey不能为空");
        }
        if (!StringUtils.hasText(req.getApiSecret())) {
            throw new BusinessException(ErrorCode.REQUIRED_PARAM_EMPTY, "创建密钥时apiSecret不能为空");
        }

        EchotikApiKey key = new EchotikApiKey();
        fillFields(key, req);
        key.setApiKeyEncrypted(AesEncryptUtil.encrypt(req.getApiKey(), aesSecretKey));
        key.setApiSecretEncrypted(AesEncryptUtil.encrypt(req.getApiSecret(), aesSecretKey));
        save(key);
        logger.info("Echotik API key created: id={}, name={}", key.getId(), key.getName());
        return toVO(key);
    }

    /**
     * 更新 API 密钥配置。
     * apiKey/apiSecret 留空则保留原有加密值，不为空则重新加密。
     */
    public EchotikApiKeyVO update(String id, EchotikApiKeySaveRequest req) {
        EchotikApiKey key = getById(id);
        if (key == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "API密钥不存在");
        }
        fillFields(key, req);
        if (StringUtils.hasText(req.getApiKey())) {
            key.setApiKeyEncrypted(AesEncryptUtil.encrypt(req.getApiKey(), aesSecretKey));
            logger.info("Echotik API key rotated: id={}", id);
        }
        if (StringUtils.hasText(req.getApiSecret())) {
            key.setApiSecretEncrypted(AesEncryptUtil.encrypt(req.getApiSecret(), aesSecretKey));
        }
        updateById(key);
        logger.info("Echotik API key updated: id={}, name={}", id, key.getName());
        return toVO(key);
    }

    /**
     * 删除 API 密钥（物理删除）
     */
    public void delete(String id) {
        EchotikApiKey key = getById(id);
        if (key == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "API密钥不存在");
        }
        removeById(id);
        logger.info("Echotik API key deleted: id={}, name={}", id, key.getName());
    }

    /**
     * 切换启用/禁用状态
     */
    public EchotikApiKeyVO toggleActive(String id) {
        EchotikApiKey key = getById(id);
        if (key == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "API密钥不存在");
        }
        Boolean current = key.getActive();
        key.setActive(current == null || !current);
        updateById(key);
        logger.info("Echotik API key toggled: id={}, active={}", id, key.getActive());
        return toVO(key);
    }

    // ==================== 私有工具 ====================

    private void fillFields(EchotikApiKey key, EchotikApiKeySaveRequest req) {
        key.setName(req.getName());
        if (req.getTotalCalls() != null) {
            key.setTotalCalls(req.getTotalCalls());
        }
        if (req.getRemainingCalls() != null) {
            key.setRemainingCalls(req.getRemainingCalls());
        }
        if (req.getAlertThreshold() != null) {
            key.setAlertThreshold(req.getAlertThreshold());
        }
        if (req.getActive() != null) {
            key.setActive(req.getActive());
        }
    }

    /**
     * 实体转脱敏 VO
     */
    EchotikApiKeyVO toVO(EchotikApiKey key) {
        boolean belowThreshold = key.getAlertThreshold() != null
                && key.getRemainingCalls() != null
                && key.getRemainingCalls() <= key.getAlertThreshold();
        return EchotikApiKeyVO.builder()
                .id(key.getId())
                .name(key.getName())
                .apiKeyMasked(maskSecret(key.getApiKeyEncrypted()))
                .apiSecretMasked(maskSecret(key.getApiSecretEncrypted()))
                .totalCalls(key.getTotalCalls())
                .remainingCalls(key.getRemainingCalls())
                .alertThreshold(key.getAlertThreshold())
                .belowThreshold(belowThreshold)
                .active(key.getActive())
                .lastUsedTime(key.getLastUsedTime())
                .createTime(key.getCreateTime())
                .updateTime(key.getUpdateTime())
                .build();
    }

    /**
     * 解密后取后 MASK_SUFFIX_LEN 位展示，解密失败返回 "****????"
     */
    private String maskSecret(String encryptedValue) {
        if (!StringUtils.hasText(encryptedValue)) {
            return "****（未设置）";
        }
        try {
            String plain = AesEncryptUtil.decrypt(encryptedValue, aesSecretKey);
            if (plain.length() <= MASK_SUFFIX_LEN) {
                return "****";
            }
            return "****" + plain.substring(plain.length() - MASK_SUFFIX_LEN);
        } catch (Exception e) {
            logger.warn("密钥解密失败，可能AES_SECRET_KEY不匹配: {}", e.getMessage());
            return "****????";
        }
    }
}
