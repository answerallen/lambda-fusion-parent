package com.lambda.fusion.authority.client.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.Data;

@Data
@TableName("la_clients")
@Schema(description = "第三方客户端")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class ClientEntity {

    /**
     * 主键
     */
    @TableId
    @Schema(description = "主键")
    private String id;
    /**
     * 客户端名称
     */
    @Schema(description = "客户端名称")
    @TableField("name")
    private String name;
    /**
     * 客户端证书
     */
    @Schema(description = "客户端密钥")
    @TableField("secret")
    private String secret;
    /**
     * 绑定IP地址
     */
    @Schema(description = "绑定IP地址")
    @TableField("hosts")
    private String hosts;
    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    @TableField("created")
    private Date created;
    /**
     * 更新时间
     */
    @Schema(description = "更新时间")
    @TableField("updated")
    private Date updated;
    /**
     * 过期时间
     */
    @Schema(description = "过期时间")
    @TableField("expired")
    private Date expired;
    /**
     * 是否可用
     */
    @Schema(description = "是否可用")
    @TableField("enabled")
    private Boolean enabled;
    /**
     * 备注
     */
    @Schema(description = "备注")
    @TableField("remarks")
    private String remarks;

    /**
     * 租户ID
     */
    @Schema(description = "租户ID")
    @TableField("tenant_id")
    private String tenantId;
}
