package com.lambda.fusion.authority.model.user;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lambda.cloud.core.annotation.AutoConverter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@AutoConverter(target = UserInfo.class)
@Getter
@Setter
@ToString
@TableName("LA_USER_INFO")
@Schema(description = "用户扩展信息")
public class UserInfoEntity {
    @TableId("USERNAME")
    @JsonIgnore
    private String username;

    @TableField("AVATAR")
    @Schema(description = "用户头像")
    private String avatar;

    @TableField("REMARK")
    @Schema(description = "用户备注")
    @Size(max = 255)
    private String remark;

    @Schema(description = "身份证号")
    @TableField("IDENTITY_ID")
    private String identityId;

    @Schema(description = "公司编号")
    @TableField(exist = false)
    private String groupNo;

    @Schema(description = "岗位编号")
    @TableField("POSITION")
    private String position;

    @Schema(description = "职工状态")
    @TableField("STATUS")
    private String status;

    @Schema(description = "员工工号")
    @TableField("EMP_NO")
    private String empNo;

    @Schema(description = "钉钉账户")
    @TableField("DD_NO")
    private String ddNo;

    @Schema(description = "钉钉昵称")
    @TableField("DD_NICK")
    private String ddNick;

    @Schema(description = "微信账户")
    @TableField("WECHAT_NO")
    private String wechatNo;

    @Schema(description = "是否需要修改密码")
    @TableField("password_reset_required")
    private Boolean updatePwd;

    @Schema(description = "扩展参数")
    @TableField("EXTEND_PARAM")
    private String extendParam;

    @Schema(description = "企业微信名称")
    @TableField("WECHAT_NAME")
    private String wechatName;

    @TableField("TENANT_ID")
    private String tenantId;
}
