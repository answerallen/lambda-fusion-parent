package com.lambda.fusion.ai.commons.support.security;

import cn.hutool.crypto.symmetric.AES;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 基于 AES 的密钥加密服务实现
 * 使用 Hutool 库提供的 AES 加密算法
 */
@Slf4j
@Service
public class AesKeyEncryptionService implements KeyEncryptionService {

    // 加密密钥前缀，用于标识已加密的密钥
    private static final String ENCRYPTED_PREFIX = "ENC:";

    @Value("${lambda.fusion.ai.security.encryption-key:}")
    private String encryptionKey;

    private AES aes;

    /**
     * 初始化 AES 加密器
     */
    private synchronized AES getAes() {
        if (aes == null) {
            if (encryptionKey == null || encryptionKey.isEmpty()) {
                log.warn("未配置加密密钥，将使用默认密钥。建议在生产环境中配置 lambda.fusion.ai.security.encryption-key");
                // 使用默认密钥（仅用于开发环境）
                encryptionKey = "0123456789abcdef"; // 16 字节
            }

            // 确保密钥长度为 16、24 或 32 字节
            if (encryptionKey.length() < 16) {
                encryptionKey = String.format("%-16s", encryptionKey).replace(' ', '0');
            } else if (encryptionKey.length() > 32) {
                encryptionKey = encryptionKey.substring(0, 32);
            }

            aes = new AES(encryptionKey.getBytes(StandardCharsets.UTF_8));
        }
        return aes;
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            // 如果已经加密，直接返回
            if (isEncrypted(plaintext)) {
                return plaintext;
            }

            String encrypted = getAes().encryptHex(plaintext);
            return ENCRYPTED_PREFIX + encrypted;
        } catch (Exception e) {
            log.error("密钥加密失败", e);
            throw new RuntimeException("密钥加密失败", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }

        try {
            // 如果不是加密格式，直接返回（兼容未加密的密钥）
            if (!isEncrypted(ciphertext)) {
                log.warn("检测到未加密的密钥，建议立即加密存储");
                return ciphertext;
            }

            String encryptedHex = ciphertext.substring(ENCRYPTED_PREFIX.length());
            return getAes().decryptStr(encryptedHex);
        } catch (Exception e) {
            log.error("密钥解密失败", e);
            throw new RuntimeException("密钥解密失败", e);
        }
    }

    @Override
    public boolean isEncrypted(String text) {
        return text != null && text.startsWith(ENCRYPTED_PREFIX);
    }
}
