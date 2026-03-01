package com.lambda.fusion.authority.model.token;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.fusion.core.pagination.Pagination;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

/**
 * <p>
 * Api Token分页查询DTO
 * </p>
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Api Token分页查询DTO")
public class ApiTokenQuery extends Pagination<ApiTokenEntity> {

    @Schema(description = "Api Token")
    private String apiToken;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "IP白名单")
    private String ipWhiteList;

    @Schema(description = "是否可用 1 启用")
    private Integer enabled;

    @Schema(description = "创建日期开始时间")
    private Date createTimeStart;

    @Schema(description = "创建日期结束时间")
    private Date createTimeEnd;

    @Schema(description = "失效日期开始时间")
    private Date expirationTimeStart;

    @Schema(description = "失效日期结束时间")
    private Date expirationTimeEnd;

    /**
     * 构建查询条件
     *
     * @return LambdaQueryWrapper<ApiTokenEntity>
     */
    @Override
    public LambdaQueryWrapper<ApiTokenEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<ApiTokenEntity> wrapper = new LambdaQueryWrapper<>();

        // API Token模糊查询
        wrapper.like(StringUtils.isNotBlank(apiToken), ApiTokenEntity::getApiToken, apiToken);

        // 描述模糊查询
        wrapper.like(StringUtils.isNotBlank(description), ApiTokenEntity::getDescription, description);

        // IP白名单模糊查询
        wrapper.like(StringUtils.isNotBlank(ipWhiteList), ApiTokenEntity::getIpWhiteList, ipWhiteList);

        // 启用状态精确查询
        wrapper.eq(enabled != null, ApiTokenEntity::getEnabled, enabled);

        // 创建时间范围查询
        wrapper.ge(createTimeStart != null, ApiTokenEntity::getCreateTime, createTimeStart);
        wrapper.le(createTimeEnd != null, ApiTokenEntity::getCreateTime, createTimeEnd);

        // 失效时间范围查询
        wrapper.ge(expirationTimeStart != null, ApiTokenEntity::getExpirationTime, expirationTimeStart);
        wrapper.le(expirationTimeEnd != null, ApiTokenEntity::getExpirationTime, expirationTimeEnd);

        return wrapper;
    }
}
