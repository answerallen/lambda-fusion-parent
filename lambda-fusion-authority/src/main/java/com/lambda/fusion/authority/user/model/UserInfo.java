package com.lambda.fusion.authority.user.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "用户扩展信息")
@SuppressFBWarnings({"UUF_UNUSED_FIELD", "CT_CONSTRUCTOR_THROW"})
public class UserInfo {
    private String userid;

    /**
     * 用户头像
     */
    private String avatar;
    /**
     * 用户备注
     */
    private String remark;

    /**
     * 身份证号
     */
    private String identityId;

    /**
     * 公司编号
     */
    private String groupNo;

    /**
     * 线路编号
     */
    private String lineNo;

    /**
     * 岗位编号
     */
    private String position;

    /**
     * 职工状态
     */
    private String status;

    /**
     * 路队编号
     */
    private String filaNo;

    /**
     * 员工工号
     */
    private String empNo;

    /**
     * 钉钉账户
     */
    private String ddNo;

    /**
     * 钉钉昵称
     */
    private String ddNick;

    /**
     * 微信账户
     */
    private String wechatNo;

    /**
     * 是否需要修改密码
     */
    private Boolean updatePwd;

    /**
     * 扩展参数
     */
    private String extendParam;

    /**
     * 企业微信名称
     */
    private String wechatName;

    /**
     * 密码修改间隔天数
     */
    private Integer passwordModifyDays;
}
