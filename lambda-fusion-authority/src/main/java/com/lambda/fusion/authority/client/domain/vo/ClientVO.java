package com.lambda.fusion.authority.client.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Date;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
@Schema(description = "客户端信息")
public class ClientVO {

    /**
     * 客户端名称
     */
    @Schema(description = "客户端名称")
    @NotBlank
    @Length(max = 30)
    private String name;
    /**
     * 绑定IP地址
     */
    @Schema(description = "绑定IP地址")
    private String hosts;
    /**
     * 过期时间
     */
    @Schema(description = "过期时间")
    private Date expired;
    /**
     * 是否可用
     */
    @Schema(description = "是否可用")
    private Boolean enabled;
    /**
     * 备注
     */
    @Length(max = 200)
    @Schema(description = "备注")
    private String remarks;
}
