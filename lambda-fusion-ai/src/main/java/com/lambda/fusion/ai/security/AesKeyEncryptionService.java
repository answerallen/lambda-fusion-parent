package com.lambda.fusion.ai.security;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 基于 AES-256-GCM 的 API Key 加解密实现。密钥由 {@code lambda.fusion.ai.security.encryption-key}
 * 配置，经 SHA-256 派生为 32 字节 AES 密钥；每次加密使用随机 12 字节 IV，
 * 密文格式为 {@code base64(IV || ciphertext+tag)}。未配置密钥时启动仅打印告警，
 * 实际调用加解密时抛 {@link AiErrorCode#LLM_ENCRYPTION_KEY_NOT_CONFIGURED}，
 * 便于仅使用无密钥提供方（如 Ollama）的环境正常启动。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class AesKeyEncryptionService implements KeyEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final AiProperties aiProperties;

    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKeySpec keySpec;

    @PostConstruct
    public void init() {
        String key = aiProperties.getSecurity().getEncryptionKey();
        if (StringUtils.isBlank(key)) {
            log.warn("未配置 lambda.fusion.ai.security.encryption-key，LLM API Key 无法加密存储");
            return;
        }
        try {
            byte[] derived = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            this.keySpec = new SecretKeySpec(derived, ALGORITHM);
            log.info("LLM API Key 加密密钥已初始化");
        } catch (Exception e) {
            throw new AiBusinessException(AiErrorCode.CONFIGURATION_ERROR, e);
        }
    }

    @Override
    public String encrypt(String plaintext) {
        if (StringUtils.isBlank(plaintext)) {
            return null;
        }
        ensureKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] output = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(cipherBytes, 0, output, iv.length, cipherBytes.length);
            return Base64.getEncoder().encodeToString(output);
        } catch (Exception e) {
            throw new AiBusinessException(AiErrorCode.CONFIGURATION_ERROR, e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (StringUtils.isBlank(ciphertext)) {
            return null;
        }
        ensureKey();
        try {
            byte[] input = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherBytes = new byte[input.length - IV_LENGTH];
            System.arraycopy(input, 0, iv, 0, IV_LENGTH);
            System.arraycopy(input, IV_LENGTH, cipherBytes, 0, cipherBytes.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AiBusinessException(AiErrorCode.LLM_API_KEY_DECRYPT_FAILED, e);
        }
    }

    private void ensureKey() {
        if (keySpec == null) {
            throw new AiBusinessException(AiErrorCode.LLM_ENCRYPTION_KEY_NOT_CONFIGURED);
        }
    }
}
