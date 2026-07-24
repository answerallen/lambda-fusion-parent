package com.lambda.fusion.ai.security;

public interface KeyEncryptionService {

    /** 加密明文，返回包含 IV 的 Base64 密文。 */
    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
