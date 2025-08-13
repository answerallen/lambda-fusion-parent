package com.lambda.fusion.authority.client.model.dto;

import cn.hutool.core.bean.BeanUtil;
import com.lambda.cloud.core.base.BaseDTO;
import com.lambda.fusion.authority.client.model.entity.ApiTokenEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;
import org.apache.commons.lang3.RandomStringUtils;

@Data
@Schema(description = "SaveTokenVO对象")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class ApiTokenInputDTO extends BaseDTO<ApiTokenInputDTO, ApiTokenEntity> implements Serializable {

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

    @Override
    public ApiTokenEntity convertToEntity() {
        ApiTokenEntity apiTokenEntity = BeanUtil.copyProperties(this, ApiTokenEntity.class);
        apiTokenEntity.setApiToken(RandomStringUtils.secure().nextAlphabetic(32));
        return apiTokenEntity;
    }
}
