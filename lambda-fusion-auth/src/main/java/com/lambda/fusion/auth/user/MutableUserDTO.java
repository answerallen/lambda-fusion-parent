package com.lambda.fusion.auth.user;

import com.lambda.fusion.auth.OrgDTO;
import com.lambda.fusion.auth.SimpleRoleDTO;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class MutableUserDTO {

    /**
     * 用户名称
     */
    private String username;

    private String password;
    /**
     * 用户昵称
     */
    private String nickname;
    /**
     * 手机号码
     */
    private String mobile;
    /**
     * 电子邮箱
     */
    private String email;
    /**
     * 创建时间
     */
    private Date createDate;
    /**
     * 工号
     */
    private String jobno;
    /**
     * 租户ID
     */
    private String tenantId;
    /**
     * 用户创建人
     */
    private String owner;
    /**
     * 是否启用
     */
    private boolean enabled;
    /**
     * 是否在线
     */
    private boolean online;
    /**
     * 是否锁定
     */
    private boolean locked;
    /**
     * 昵称拼音缩写
     */
    private String nicknameAbbr;
    /**
     * 创建人用户
     */
    private String createAccount;
    /**
     * 组织信息
     */
    private OrgDTO org;
    /**
     * 角色信息
     */
    private List<SimpleRoleDTO> authorities;
    /**
     * 扩展属性
     */
    private UserInfoDTO props;

    private boolean self;
    /**
     * 最后离线时间
     */
    private Date offlineTime;
    /**
     * 创建人
     */
    private String creator;
    /**
     * 禁止批被分配
     */
    private Boolean disAllocation;
    /**
     * 是否可以被操作
     */
    private Boolean noPermission;
    /**
     * 过期时间
     */
    private Date expiredTime;
    /**
     * 用户新增字段信息
     */
    private Map<String, String> personal;

    public String id() {
        return this.getUsername();
    }
}
