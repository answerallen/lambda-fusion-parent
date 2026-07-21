package com.lambda.fusion.ai.security;

/**
 * LLM API Key 加解密服务。
 *
 * <p>用于对存储在数据库中的 LLM 提供方 API Key 进行可逆加密，避免明文落库。
 *
 * @author Jin
 */
public interface KeyEncryptionService {

    /** 加密，返回 base64 编码的密文（含 IV）。 */
    String encrypt(String plaintext);

    /** 解密，返回明文。 */
    String decrypt(String ciphertext);
}
