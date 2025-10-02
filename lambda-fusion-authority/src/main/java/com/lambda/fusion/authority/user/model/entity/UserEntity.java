package com.lambda.fusion.authority.user.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("la_users")
public class UserEntity {

    @TableId(value = "userid")
    private String userid;

    @TableField("PASSWORD")
    private String password;

    @TableField("NICKNAME")
    private String nickname;

    @TableField("MOBILE")
    private String mobile;

    @TableField("EMAIL")
    private String email;

    @TableField("CREATE_DATE")
    private Date createDate;

    @TableField("ENABLED")
    private Integer enabled;

    @TableField("TENANT_ID")
    private String tenantId;

    @TableField("OWNER")
    private String owner;

    @TableField("JOBNO")
    private String jobno;

    @TableField("NICKNAME_ABBR")
    private String nicknameAbbr;

    @TableField("CREATOR")
    private String creator;

    @TableField("EXPIRED_TIME")
    private Date expiredTime;
}
