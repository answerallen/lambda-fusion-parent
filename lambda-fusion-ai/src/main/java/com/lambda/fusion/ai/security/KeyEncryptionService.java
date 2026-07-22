package com.lambda.fusion.ai.security;

/**
 * LLM API Key 加解密服务。
 *
 * <p>用于对存储在数据库中的 LLM 提供方 API Key 进行可逆加密，避免明文落库。
 *
 * @author Jin
 */
public interface KeyEncryptionService {

    /** 加密明文，返回包含 IV 的 Base64 密文。 */
    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
