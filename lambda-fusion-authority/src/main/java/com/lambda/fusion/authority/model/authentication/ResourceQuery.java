package com.lambda.fusion.authority.model.authentication;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * 简单资源查询数据传输对象
 * 用于替代 getAllResourcesSimple 方法中的 Map 参数，提供类型安全的查询参数封装
 */
@Data
@Schema(description = "简单资源查询参数")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class ResourceQuery {

    /**
     * 资源模式
     * 0: 系统资源（后台管理）
     * 1: App资源（移动端）
     */
    @Schema(description = "资源模式(0:系统资源,1:App资源)")
    private Integer mode;

    /**
     * 资源类型列表
     * 可以指定多个资源类型进行查询
     */
    @Schema(description = "资源类型列表")
    private List<Integer> types;
}
