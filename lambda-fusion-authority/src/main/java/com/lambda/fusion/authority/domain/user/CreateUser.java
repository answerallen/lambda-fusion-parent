package com.lambda.fusion.authority.domain.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.annotation.FieldMapping;
import com.lambda.cloud.core.shared.BaseDTO;
import com.lambda.fusion.authority.domain.organization.SimpleOrganization;
import com.lambda.fusion.authority.domain.role.SimpleRole;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.Length;

/**
 * 易变的用户信息
 */
@EqualsAndHashCode(callSuper = true)
@AutoConverter(target = UserEntity.class)
@Data
@Schema(description = "用户信息")
public class CreateUser extends BaseDTO<UserEntity> {

    @Schema(description = "用户名称")
    @NotNull(message = "username not found")
    private String username;

    @Hidden
    private String password;

    @Length(max = 16, message = "用户昵称长度不能超过16个字符")
    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "手机号码")
    @NotNull(message = "mobile not found")
    private String mobile;

    @Schema(description = "电子邮箱")
    private String email;

    @Schema(description = "租户ID")
    private String tenantId;

    @FieldMapping(target = "enabled", ignore = true)
    @Schema(description = "是否启用")
    private boolean enabled;

    @Schema(description = "是否锁定")
    private boolean locked;

    @JsonProperty("organization")
    @Schema(description = "组织信息")
    private SimpleOrganization organization;

    @Schema(description = "角色信息")
    @JsonProperty("authorities")
    private List<SimpleRole> authorities;

    @Schema(description = "扩展属性")
    @Valid
    @JsonProperty("props")
    private UserInfo props;

    @Schema(description = "过期时间")
    private Date expiredTime;

    @Schema(description = "用户新增字段信息")
    @JsonProperty("personal")
    private Map<String, Object> personal;
}
