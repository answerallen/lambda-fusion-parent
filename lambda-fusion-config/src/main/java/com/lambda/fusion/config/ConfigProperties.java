package com.lambda.fusion.config;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.util.ProxyUtils;

import java.io.IOException;

@Slf4j
@Setter
@Getter
@SuppressFBWarnings("EI_EXPOSE_REP")
@ConfigurationProperties(prefix = "lambda.fusion.config")
@JsonPropertyOrder(alphabetic = true)
public class ConfigProperties {

    private String title = "快速开发平台";

    private String copyright = "版权 &copy; Lambda Fusion";

    @Schema(description = "密码校验规则")
    private Password password = new Password();

    @Schema(description = "系统版本号")
    private String version;

    @Schema(description = "安全相关配置")
    private Security security = new Security();

    @Schema(description = "数据库相关配置")
    private Database database = new Database();

    @JsonSerialize(using = Customize.Serialize.class)
    private Customize customize;

    public ConfigProperties(Customize customize) {
        this.customize = customize;
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
    public static class Security {

        private transient String privateKey;

        private transient String publicKey;
    }

    @Getter
    @Setter
    public static class Database {

        private String selectConfigsSql =
                "SELECT property_key, property_value, application FROM la_configs WHERE application = ? OR application = 'public'";

        private String checkConfigsChangedSql =
                "SELECT MAX(update_time) FROM la_configs WHERE application = ? OR application = 'public'";
    }

    @Slf4j
    @Data
    public static class Customize {

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
