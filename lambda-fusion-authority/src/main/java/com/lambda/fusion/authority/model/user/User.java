package com.lambda.fusion.authority.model.user;

import cn.hutool.core.collection.CollUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lambda.fusion.authority.model.organization.SimpleOrganization;
import com.lambda.fusion.authority.model.role.SimpleRole;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
@Schema(description = "用户信息")
public class User {

    @Schema(description = "用户名称")
    private String username;

    @Hidden
    private String password;

    @Length(max = 16, message = "用户昵称长度不能超过16个字符")
    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "手机号码")
    private String mobile;

    @Schema(description = "电子邮箱")
    private String email;

    @Schema(description = "创建时间")
    private Date createdAt;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "是否启用")
    private boolean enabled;

    @Schema(description = "是否在线")
    private boolean online;

    @Schema(description = "是否锁定")
    private boolean locked;

    @JsonProperty("organization")
    @Schema(description = "组织信息")
    private SimpleOrganization organization;

    public String getOrgName() {
        if (organization == null) {
            return "-";
        }
        return organization.getAlias();
    }

    @Schema(description = "角色信息")
    @JsonProperty("authorities")
    private List<SimpleRole> authorities;

    public String getRoleName() {
        if (CollUtil.isEmpty(authorities)) {
            return "-";
        }
        return authorities.stream().map(SimpleRole::getAlias).collect(Collectors.joining("|"));
    }

    private boolean self;

    @Schema(description = "最后离线时间")
    private Date offlineTime;

    @Schema(description = "创建人")
    private String createdBy;

    @Schema(description = "禁止批被分配")
    private Boolean disableAssignment;

    @Schema(description = "是否可以被操作")
    private Boolean disableOperations;

    @Schema(description = "过期时间")
    private Date expiredTime;

    @Schema(description = "用户新增字段信息")
    @JsonProperty("personal")
    private Map<String, Object> personal;

    @Schema(description = "扩展属性")
    @Valid
    @JsonProperty("props")
    private UserInfo props;
}
