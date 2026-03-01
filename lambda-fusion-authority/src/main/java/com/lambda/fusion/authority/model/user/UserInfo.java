package com.lambda.fusion.authority.model.user;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@AutoConverter(target = UserInfoEntity.class)
@Getter
@Setter
@Schema(description = "用户扩展信息")
public class UserInfo extends BaseDTO<UserInfoEntity> {
    private String username;

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
     * 岗位编号
     */
    private String position;

    /**
     * 职工状态
     */
    private String status;

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
     * 扩展参数
     */
    private String extendParam;

    /**
     * 企业微信名称
     */
    private String wechatName;

    /**
     * 是否需要修改密码
     */
    private Boolean passwordResetRequired = true;

    /**
     * 密码修改间隔天数
     */
    private Integer passwordModifyDays;
}
