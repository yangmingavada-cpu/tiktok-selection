package com.tiktok.selection.common;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 对称加密工具类
 * 用于配置文件中敏感数据（LLM API Key、第三方密钥等）的加解密
 *
 * <p>遵循阿里巴巴Java开发手册（黄山版）安全规约第10条：配置文件中的密码需要加密。
 * 使用 AES-256-GCM 认证加密模式，同时保证机密性和完整性。
 *
 * <p>密文格式：Base64( IV(12B) || CipherText || GCM-Tag(16B) )
 * IV 随机生成，每次加密结果不同（防重放）。
 *
 * <p>密钥管理：32字节原始密钥的Base64编码，必须通过环境变量注入，
 * 禁止硬编码在配置文件中。
 *
 * @author system
 * @date 2026/03/24
 */
public final class AesEncryptUtil {

    private AesEncryptUtil() {}

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    /** GCM 推荐 IV 长度：96 bits */
    private static final int GCM_IV_LENGTH = 12;
    /** GCM 认证标签长度：128 bits */
    private static final int GCM_TAG_LENGTH_BITS = 128;

    /**
     * 加密明文字符串。
     * 每次调用生成随机 IV，密文结果不可重现（防暴力枚举）。
     *
     * @param plainText 待加密的明文
     * @param base64Key AES-256 密钥的 Base64 编码（32字节原始密钥）
     * @return Base64 编码的密文（含 IV 前缀）
     * @throws IllegalStateException 加密失败时抛出（避免吞掉异常导致明文泄露）
     */
    public static String encrypt(String plainText, String base64Key) {
        try {
            SecretKey key = toSecretKey(base64Key);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 拼接 IV + 密文（GCM-Tag 已包含在 cipherText 末尾）
            byte[] combined = new byte[GCM_IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(cipherText, 0, combined, GCM_IV_LENGTH, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    /**
     * 解密密文字符串。
     * GCM 模式会验证数据完整性，密文被篡改时将抛出异常而非返回错误数据。
     *
     * @param encryptedBase64 Base64 编码的密文（加密时生成的格式）
     * @param base64Key       AES-256 密钥的 Base64 编码
     * @return 原始明文字符串
     * @throws IllegalStateException 解密失败（密钥错误或数据被篡改）时抛出
     */
    public static String decrypt(String encryptedBase64, String base64Key) {
        try {
            SecretKey key = toSecretKey(base64Key);
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);

            if (combined.length <= GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Invalid ciphertext: too short");
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decryption failed — check encryption key or ciphertext integrity", e);
        }
    }

    private static SecretKey toSecretKey(String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("AES key must be 32 bytes (256 bits), got: " + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
