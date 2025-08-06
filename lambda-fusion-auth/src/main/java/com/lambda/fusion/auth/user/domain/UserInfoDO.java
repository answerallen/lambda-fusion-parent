package com.lambda.fusion.auth.user.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lambda.fusion.core.base.M1Expanded;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.validator.constraints.Length;


@Getter
@Setter
@ToString
@TableName("LA_USER_INFO")
@Schema(description = "用户扩展信息")
public class UserInfoDO implements M1Expanded {
    @TableId
    @JsonIgnore
    private String userid;

    @TableField
    @Schema(description = "用户头像")
    private String avatar;
    @TableField
    @Schema(description = "用户备注")
    @Length(max = 255)
    private String remark;

    @Schema(description = "身份证号")
    @TableField("IDENTITY_ID")
    private String identityId;

    @Schema(description = "公司编号")
    @TableField("GROUP_NO")
    private String groupNo;

    @Schema(description = "线路编号")
    @TableField("LINE_NO")
    private String lineNo;

    @Schema(description = "岗位编号")
    @TableField("POSITION")
    private String position;

    @Schema(description = "职工状态")
    @TableField("STATUS")
    private String status;

    @Schema(description = "路队编号")
    @TableField("FILA_NO")
    private String filaNo;

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
    @TableField("ISUPDATEPWD")
    private Boolean updatePwd;

    @Schema(description = "扩展参数")
    @TableField("EXTEND_PARAM")
    private String extendParam;

    @Schema(description = "企业微信名称")
    @TableField("WECHAT_NAME")
    private String wechatName;

    @Override
    public void id(String id) {
        setUserid(id);
    }

    @Override
    public String id() {
        return getUserid();
    }


}

