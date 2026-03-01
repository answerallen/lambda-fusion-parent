package com.lambda.fusion.authority.domain.user;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.Data;

@Data
@TableName("LA_USER_PASSWORD_LOGS")
@SuppressFBWarnings("EI_EXPOSE_REP")
@Schema(description = "日志表实体类")
public class UserPasswordEntity {

    @TableId
    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "用户ID")
    @TableField("USERNAME")
    private String username;

    @Schema(description = "用户密码")
    @TableField("PASSWORD")
    private String password;

    @Schema(description = "修改密码日期")
    @TableField("UPDATED_AT")
    private Date updateTime;
}
