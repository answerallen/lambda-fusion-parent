package com.lambda.fusion.config.utils;

import static com.lambda.fusion.config.ConfigConstants.Encryption.AES_PADDING;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.symmetric.AES;
import com.lambda.fusion.config.ConfigProperties;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class EncryptUtils {

    /**
     * 解密配置值
     *
     * @param encryptedValue   待解密的字符串
     * @param configProperties 配置属性，用于判断是否启用加密以及获取密钥
     * @return 解密后的字符串，如果未启用加密或解密失败则返回原字符串
     */
    public static String decrypt(String encryptedValue, ConfigProperties configProperties) {
        if (configProperties == null) {
            return encryptedValue;
        }

        // 检查是否是加密字符串，这里假设加密字符串以 "ENC(" 开头并以 ")" 结尾
        if (encryptedValue.startsWith("ENC(") && encryptedValue.endsWith(")")) {
            String realEncryptedValue = encryptedValue.substring(4, encryptedValue.length() - 1);
            try {
                // TODO: 这里的AES密钥需要从ConfigProperties中获取，并且需要与加密时的密钥一致。
                String aesKey = configProperties.getSecurity().getPrivateKey();
                if (CharSequenceUtil.isBlank(aesKey)) {
                    log.warn("AES key is blank, cannot decrypt value. Returning original value.");
                    return encryptedValue;
                }
                AES aes = new AES(Mode.ECB, Padding.valueOf(AES_PADDING), CharSequenceUtil.bytes(aesKey));
                return aes.decryptStr(realEncryptedValue);
            } catch (Exception e) {
                log.error("Failed to decrypt config value: {}. Returning original value.", encryptedValue, e);
                return encryptedValue;
            }
        }
        return encryptedValue;
    }
}
