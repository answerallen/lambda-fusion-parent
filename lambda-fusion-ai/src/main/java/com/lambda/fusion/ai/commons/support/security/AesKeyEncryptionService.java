package com.lambda.fusion.ai.commons.support.security;

import cn.hutool.core.util.HexUtil;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 基于 AES-256-GCM 的密钥加密服务实现
 *
 * 安全特性：
 * - 使用 AES-256-GCM 认证加密模式
 * - 每次加密生成随机 IV（初始化向量）
 * - 提供 机密性 + 完整性 + 真实性 保证
 * - 向后兼容未加密的密钥
 */
@Slf4j
@Service
public class AesKeyEncryptionService implements KeyEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String ENCRYPTED_PREFIX = "ENC:";

    @Value("${lambda.fusion.ai.security.encryption-key:}")
    private String encryptionKey;

    private volatile SecretKeySpec secretKey;

    private SecretKeySpec getSecretKey() {
        if (secretKey == null) {
            synchronized (this) {
                if (secretKey == null) {
                    String key = encryptionKey;
                    if (key == null || key.isEmpty()) {
                        log.warn("未配置加密密钥，将使用默认密钥。生产环境必须配置 lambda.fusion.ai.security.encryption-key");
                        key = "default-dev-key-do-not-use-in-production";
                    }

                    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                    keyBytes = Arrays.copyOf(keyBytes, 32);
                    secretKey = new SecretKeySpec(keyBytes, "AES");
                }
            }
        }
        return secretKey;
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        if (isEncrypted(plaintext)) {
            return plaintext;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), spec);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return ENCRYPTED_PREFIX + HexUtil.encodeHexStr(combined);
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

        if (!isEncrypted(ciphertext)) {
            log.warn("检测到未加密的密钥，建议立即加密存储");
            return ciphertext;
        }

        try {
            String encryptedHex = ciphertext.substring(ENCRYPTED_PREFIX.length());
            byte[] combined = HexUtil.decodeHex(encryptedHex);

            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec);

            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
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
