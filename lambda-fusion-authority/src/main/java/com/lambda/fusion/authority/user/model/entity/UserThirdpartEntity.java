package com.lambda.fusion.authority.user.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("la_user_thirdpart")
public class UserThirdpartEntity {

    @TableField("USERNAME")
    private String username;

    @TableField("LOGIN_TYPE")
    private String loginType;

    @TableField("OPEN_ID")
    private String openId;
}
