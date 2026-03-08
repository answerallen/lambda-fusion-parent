package com.lambda.fusion.permission;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@SuppressFBWarnings("EI_EXPOSE_REP")
@ConfigurationProperties(prefix = PermissionConstants.PREFIX)
public class PermissionProperties {
    private boolean enabled = true;
    private String mode = PermissionConstants.DEFAULT_MODE;
    private Client client = new Client();
    private Server server = new Server();

    @Data
    public static class Client {
        private boolean checkEnabled = true;
        private boolean denyUnmatched = true;
        private boolean pushEnabled = true;
        private boolean failFast = false;
        private String resourcePath = "META-INF/permissions/api-permissions.json";
        private String reportPath = "/permission/apis/report";
        private String serverBaseUrl;
        private String authToken;
    }

    @Data
    public static class Server {
        private boolean exposeApi = true;
        private String authToken;
    }
}
