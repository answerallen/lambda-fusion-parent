package com.lambda.fusion.ai.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.exception.AiBusinessException;
import org.junit.jupiter.api.Test;

class AesKeyEncryptionServiceTest {

    private AesKeyEncryptionService newService(String key) {
        AiProperties props = new AiProperties();
        props.getSecurity().setEncryptionKey(key);
        AesKeyEncryptionService svc = new AesKeyEncryptionService(props);
        svc.init();
        return svc;
    }

    @Test
    void encryptDecryptRoundTrip() {
        AesKeyEncryptionService svc = newService("any-passphrase-works-via-sha256-derivation");
        String plaintext = "sk-abcdef1234567890";
        String cipher = svc.encrypt(plaintext);
        assertThat(cipher).isNotEqualTo(plaintext).isNotBlank();
        assertThat(svc.decrypt(cipher)).isEqualTo(plaintext);
    }

    @Test
    void eachEncryptionProducesDifferentCiphertext() {
        AesKeyEncryptionService svc = newService("some-key");
        String c1 = svc.encrypt("same-secret");
        String c2 = svc.encrypt("same-secret");
        assertThat(c1).isNotEqualTo(c2);
        assertThat(svc.decrypt(c1)).isEqualTo("same-secret");
        assertThat(svc.decrypt(c2)).isEqualTo("same-secret");
    }

    @Test
    void encryptBlankReturnsNull() {
        AesKeyEncryptionService svc = newService("some-key");
        assertThat(svc.encrypt(null)).isNull();
        assertThat(svc.encrypt("")).isNull();
    }

    @Test
    void decryptWithoutKeyThrows() {
        AesKeyEncryptionService svc = newService(null);
        assertThatThrownBy(() -> svc.decrypt("dGVzdA==")).isInstanceOf(AiBusinessException.class);
    }
}
