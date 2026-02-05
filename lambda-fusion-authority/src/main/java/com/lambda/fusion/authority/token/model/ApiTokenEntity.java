package com.lambda.fusion.authority.token.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.Data;

/**
 * <p>
 * Api Token授权信息
 * </p>
 *
 */
@Data
@TableName("LA_API_TOKEN")
@Schema(description = "Api Token授权信息")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class ApiTokenEntity {

    @TableId(value = "ID", type = IdType.ASSIGN_ID)
    private String id;

    @Schema(description = "Api Token")
    @TableField("API_TOKEN")
    private String apiToken;

    @Schema(description = "描述")
    @TableField("DESCRIPTION")
    private String description;

    @Schema(description = "IP白名单，多个用','分割")
    @TableField("IP_WHITE_LIST")
    private String ipWhiteList;

    @Schema(description = "是否可用 1 启用")
    @TableField("ENABLED")
    private Integer enabled;

    @Schema(description = "创建日期")
    @TableField("CREATED_AT")
    private Date createTime;

    @Schema(description = "失效日期")
    @TableField("EXPIRATION_TIME")
    private Date expirationTime;
}
