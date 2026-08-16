package com.lambda.fusion.ai.apps.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 应用发布视图（管理端）。仅承载发布事实，不含任何运行配置；
 * 后端只返回 publishCode，不保存也不拼接部署域名（URL 由前端按当前 origin 生成）。
 *
 * @author Jin
 */
@Data
@Schema(description = "应用发布信息")
public class AppPublication {

    @Schema(description = "应用ID")
    private String appId;

    @Schema(description = "发布代码(可空=从未发布)")
    private String publishCode;

    @Schema(description = "发布状态: UNPUBLISHED|PUBLISHED")
    private String publishStatus;

    @Schema(description = "最近一次成功发布时间")
    private LocalDateTime publishedAt;
}
