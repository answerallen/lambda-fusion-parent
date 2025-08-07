package com.lambda.fusion.auth.user.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.Data;

@Data
@TableName("LA_USER_UPDATE_PWD_LOGS")
@Schema(description = "日志表实体类")
public class UserUpdatePwdLog {

    @TableId
    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "用户ID")
    @TableField("USER_ID")
    private String userName;

    @Schema(description = "用户密码")
    @TableField("PASSWORD")
    private String passWord;

    @Schema(description = "修改密码日期")
    @TableField("UPDATETIME")
    private Date updateTime;
}
