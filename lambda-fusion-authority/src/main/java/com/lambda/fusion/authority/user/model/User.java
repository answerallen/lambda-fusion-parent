package com.lambda.fusion.authority.user.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lambda.fusion.authority.organization.domain.OrganizationSummary;
import com.lambda.fusion.authority.role.model.SimpleRole;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * 易变的用户信息
 */
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
    private Date createDate;

    @Schema(description = "工号")
    private String jobno;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "用户创建人")
    private String owner;

    @Schema(description = "是否启用")
    private boolean enabled;

    @Schema(description = "是否在线")
    private boolean online;

    @Schema(description = "是否锁定")
    private boolean locked;

    @Schema(description = "昵称拼音缩写")
    private String nicknameAbbr;

    @Schema(description = "创建人用户")
    private String createAccount;

    @JsonProperty("organization")
    @Schema(description = "组织信息")
    private OrganizationSummary org;

    @Schema(description = "角色信息")
    @JsonProperty("authorities")
    private List<SimpleRole> authorities;

    @Schema(description = "扩展属性")
    @Valid
    @JsonProperty("props")
    private UserInfo props;

    private boolean self;

    @Schema(description = "最后离线时间")
    private Date offlineTime;

    @Schema(description = "创建人")
    private String creator;

    @Schema(description = "禁止批被分配")
    private Boolean disAllocation;

    @Schema(description = "是否可以被操作")
    private Boolean noPermission;

    @Schema(description = "过期时间")
    private Date expiredTime;

    @Schema(description = "用户新增字段信息")
    @JsonProperty("personal")
    private Map<String, String> personal;
}
