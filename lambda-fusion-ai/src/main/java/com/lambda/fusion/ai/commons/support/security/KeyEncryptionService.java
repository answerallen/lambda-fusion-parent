package com.lambda.fusion.ai.commons.support.security;

/**
 * 密钥加密/解密服务接口
 * 用于安全地存储和检索敏感信息（如 API 密钥）
 */
public interface KeyEncryptionService {

    /**
     * 加密密钥
     *
     * @param plaintext 明文密钥
     * @return 加密后的密钥
     */
    String encrypt(String plaintext);

    /**
     * 解密密钥
     *
     * @param ciphertext 加密后的密钥
     * @return 解密后的明文密钥
     */
    String decrypt(String ciphertext);

    /**
     * 检查密钥是否已加密
     *
     * @param text 待检查的文本
     * @return true 如果已加密，false 如果是明文
     */
    boolean isEncrypted(String text);
}
