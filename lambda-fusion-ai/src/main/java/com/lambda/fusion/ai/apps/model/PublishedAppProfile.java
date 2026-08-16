package com.lambda.fusion.ai.apps.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 发布应用公开资料（匿名安全视图）。仅承载名片信息，不含 appId、tenantId 与任何运行配置；
 * 不替代登录与授权，访问控制由 access 接口完成。
 *
 * @author Jin
 */
@Data
@Schema(description = "发布应用公开资料")
public class PublishedAppProfile {

    @Schema(description = "发布代码")
    private String publishCode;

    @Schema(description = "应用名称")
    private String name;

    @Schema(description = "应用头像")
    private String avatar;

    @Schema(description = "应用描述")
    private String description;
}
