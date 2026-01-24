package com.lambda.fusion.autoconfig;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.util.ProxyUtils;
import org.springframework.core.env.Environment;

@Slf4j
@Setter
@Getter
@SuppressFBWarnings("EI_EXPOSE_REP")
@Schema(description = "应用配置信息")
@ConfigurationProperties(prefix = "lambda.fusion.config")
@JsonPropertyOrder(alphabetic = true)
public class ConfigProperties implements InitializingBean {

    @Schema(description = "系统名称")
    private String title = "快速开发平台";

    @Schema(description = "系统版权")
    private String copyright = "版权 &copy; Lambda Fusion";

    @Schema(description = "登出相关配置")
    private Logout logout = new Logout();

    @Schema(description = "密码校验规则")
    private Password password = new Password();

    @Schema(description = "系统版本号")
    private String version;

    @Schema(description = "Axios相关配置")
    private Axios axios = new Axios();

    @Schema(description = "安全相关配置")
    private Security security = new Security();

    @Schema(description = "数据库相关配置")
    private Database database = new Database();

    @JsonSerialize(using = Customize.Serialize.class)
    private Customize customize;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient Environment environment;

    /**
     * 兼容之前的版本，未来版本会替换
     */
    public boolean isEncryptOnlineConfig() {
        return security.isConfigEncryptEnabled();
    }

    /**
     * 是否开启验证码模式
     * 兼容之前的版本，未来版本会替换
     */
    public boolean isCaptcha() {
        return this.security.isFormVerifyEnabled();
    }

    public ConfigProperties(Environment environment, Customize customize) {
        this.customize = customize;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        //        this.password.setMode(environment.getProperty(AUTHORIZE_PASSWORD_STRATEGY_MODE, "FIXED"));
        //        this.password.setDefaultValue(environment.getProperty(AUTHORIZE_PASSWORD_CUSTOMIZE, "123456"));
        //        this.axios.setHiddenMethodEnabled(environment.getProperty(HTTP_METHOD_HIDDEN_ENABLED, boolean.class,
        // false));
        //        this.security.setFormVerifyEnabled(environment.getProperty(SERVER_CAPTCHA_ENABLED, boolean.class,
        // false));
        //        this.security.setPublicKey(environment.getProperty(RSA_ENCRYPT_PUBLIC_KEY));
        //        this.security.setPrivateKey(environment.getProperty(RSA_ENCRYPT_PRIVATE_KEY));
    }

    @Getter
    @Setter
    public static class Logout {

        private int timeout = 0;
    }

    @Getter
    @Setter
    public static class Password {
        private String mode = "FIXED";
        private String regExp = "";
        private String message = "";
        private Boolean forceChange = false;
        private String defaultValue = "";
    }

    @Getter
    @Setter
    public static class Axios {
        @Schema(description = "默认超时时间")
        private int timeout = 30000;

        @Schema(description = "是否隐藏请求方法")
        private boolean hiddenMethodEnabled = false;
    }

    @Getter
    @Setter
    public static class Security {
        @Schema(description = "是否开启配置加密")
        private boolean configEncryptEnabled;

        @Schema(description = "是否开启验证码模式")
        private boolean formVerifyEnabled;
        /**
         * 加密私钥
         */
        private transient String privateKey;

        /**
         * 加密公钥
         */
        private transient String publicKey;

        public boolean isConfigEncryptEnabled() {
            return this.configEncryptEnabled
                    && StringUtils.isNotBlank(this.privateKey)
                    && StringUtils.isNotBlank(this.publicKey);
        }
    }

    @Getter
    @Setter
    public static class Database {
        // 查询配置的SQL语句
        private String selectConfigsSql =
                "SELECT property_key, property_value, application FROM la_configs WHERE application = ? OR application = 'public'";

        // 检查配置变更的SQL语句
        private String checkConfigsChangedSql =
                "SELECT MAX(update_time) FROM la_configs WHERE application = ? OR application = 'public'";
    }

    @Slf4j
    @Data
    public static class Customize {
        /**
         * 开启多租户模式
         */
        private boolean tenantEnabled;
        /**
         * 开启角色
         */
        private boolean roleEnabled = true;
        /**
         * 开启数据角色
         */
        private boolean dataRoleEnabled = false;

        public static class Serialize extends JsonSerializer<Customize> {
            @Override
            public void serialize(Customize customize, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                Customize target = ProxyUtils.getTargetObject(customize);
                gen.writeObject(target);
            }
        }
    }
}
