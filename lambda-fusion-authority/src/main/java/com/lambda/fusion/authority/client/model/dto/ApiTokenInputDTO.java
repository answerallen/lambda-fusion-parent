package com.lambda.fusion.authority.client.model.dto;

import com.lambda.cloud.core.shared.BaseDTO;
import com.lambda.fusion.authority.client.model.entity.ApiTokenEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "SaveTokenVO对象")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class ApiTokenInputDTO extends BaseDTO<ApiTokenEntity> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "IP白名单，多个用','分割")
    private String ipWhiteList;

    @Schema(description = "是否可用 1 启用")
    @NotNull(message = " 启用状态不能为空")
    private Integer enabled;

    @Schema(description = "失效时间")
    @NotNull(message = " 失效时间不能为空")
    private Date expirationTime;
}
