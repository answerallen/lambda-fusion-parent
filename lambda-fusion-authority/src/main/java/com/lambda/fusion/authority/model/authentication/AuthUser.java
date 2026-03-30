package com.lambda.fusion.authority.model.authentication;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.annotation.FieldMapping;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.fusion.core.convert.FusionConvertFunctions;
import com.lambda.fusion.core.identity.UserDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.Data;

/**
 * 简单用户视图对象
 * 用于封装用户的基本信息
 */
@AutoConverter(
        target = UserDetails.class,
        uses = FusionConvertFunctions.class,
        fieldMappings = {
            @FieldMapping(target = "accountExpired", source = "expiredTime", qualifiedByName = "mapAccountExpired"),
            @FieldMapping(target = "accountLocked", source = "enabled", qualifiedByName = "mapAccountLocked"),
            @FieldMapping(target = "roles", source = "authorities"),
        })
@Data
@Schema(description = "简单用户信息")
public class AuthUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户名
     */
    @Schema(description = "用户名")
    private String username;

    /**
     * 用户昵称
     */
    @Schema(description = "用户昵称")
    private String nickname;

    /**
     * 用户密码
     */
    @Schema(description = "用户密码")
    private String password;

    /**
     * 组织机构ID
     */
    @Schema(description = "组织机构ID")
    private String orgId;

    /**
     * 租户ID
     */
    @Schema(description = "租户ID")
    private String tenantId;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用")
    private Boolean enabled;

    /**
     * 过期时间
     */
    @Schema(description = "过期时间")
    private LocalDateTime expiredTime;

    /**
     * 用户权限集合
     */
    @Schema(description = "用户权限集合")
    private Set<String> authorities;

    public UserDetails toUserDetails() {
        return ConvertUtils.convert(this);
    }
}
