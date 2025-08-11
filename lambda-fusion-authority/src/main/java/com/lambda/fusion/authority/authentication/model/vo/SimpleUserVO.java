package com.lambda.fusion.authority.authentication.model.vo;

import cn.hutool.core.date.DateUtil;
import com.lambda.fusion.core.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.Data;

/**
 * 简单用户视图对象
 * 用于封装用户的基本信息
 */
@Data
@Schema(description = "简单用户信息")
public class SimpleUserVO {

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

    /**
     * 转换为User对象
     *
     * @return User对象
     */
    public User toUser() {
        User user = new User();
        user.setUsername(this.username);
        user.setNickname(this.nickname);
        user.setPassword(this.password);
        user.setOrgId(this.orgId);
        user.setTenantId(this.tenantId);
        user.setAccountLocked(this.enabled);
        user.setAccountExpired(expiredTime.isAfter(LocalDateTime.now()));
        user.setRoles(this.authorities);
        return user;
    }
}