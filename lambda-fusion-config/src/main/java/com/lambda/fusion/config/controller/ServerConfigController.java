package com.lambda.fusion.config.controller;

import static com.lambda.fusion.config.ConfigConstants.Encryption.*;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.RSA;
import cn.hutool.crypto.symmetric.AES;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.fusion.autoconfig.ConfigProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统配置管理")
@RefreshScope
@RestController
@RequestMapping("/public/config/server")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class ServerConfigController {
    private static final Logger log = LoggerFactory.getLogger(ServerConfigController.class);

    private final ConfigProperties config;
    private ObjectMapper objectMapper;

    public ServerConfigController(ConfigProperties config) {
        this.config = config;
    }

    @Autowired
    public void setHttpMessageConverter(MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter) {
        this.objectMapper = mappingJackson2HttpMessageConverter.getObjectMapper();
    }

    @GetMapping
    @Operation(summary = "获取服务配置信息")
    public Object getServerConfig(@RequestParam(required = false) String sessionKey) {
        ConfigProperties.Security security = config.getSecurity();
        if (security.isConfigEncryptEnabled()) {
            Assert.hasText(sessionKey, CONFIG_ENCRYPT_NO_KEY);
            try {
                // 私钥解密会话密钥得到AES密钥
                RSA rsa = new RSA(security.getPrivateKey(), security.getPublicKey());
                String aesKey = rsa.decryptStr(sessionKey, KeyType.PrivateKey);
                // 使用AES密钥加密数据
                AES aes = new AES(Mode.ECB.name(), AES_PADDING, CharSequenceUtil.bytes(aesKey));
                return aes.encryptBase64(objectMapper.writeValueAsBytes(config));
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                throw new IllegalArgumentException(CONFIG_ENCRYPT_SECURITY_KEY_ERROR);
            }
        }
        return config;
    }
}
