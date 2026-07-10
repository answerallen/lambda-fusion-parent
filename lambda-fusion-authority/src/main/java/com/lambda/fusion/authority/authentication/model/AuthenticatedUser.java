package com.lambda.fusion.authority.authentication.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "登陆用户信息")
public class AuthenticatedUser implements Serializable {
    /**
     * 头像
     */
    private String avatar;
    /**
     * 用户昵称
     */
    private String realName;
    /**
     * 用户角色
     */
    private List<String> roles;
    /**
     * 用户id
     */
    private String userId;
    /**
     * 用户名
     */
    private String username;

    /**
     * 用户描述
     */
    private String desc;

    /**
     * 首页地址
     */
    private String homePath;

    /**
     * accessToken
     */
    private String token;
}
