package com.lambda.fusion.authority.client.model;

import cn.hutool.core.util.IdUtil;
import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import com.lambda.fusion.core.utils.AuthUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

@AutoConverter(target = ClientEntity.class)
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "客户端信息")
public class UpsertClient extends BaseDTO<ClientEntity> {
    /**
     * 客户端名称
     */
    @Schema(description = "客户端名称")
    @NotBlank
    @Size(max = 30)
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
    @Size(max = 200)
    @Schema(description = "备注")
    private String remarks;

    public ClientEntity toEntity() {
        ClientEntity entity = super.toEntity();
        String tenantId = AuthUtils.getTenantId();
        if (StringUtils.isNotBlank(tenantId)) {
            entity.setTenantId(tenantId);
        }
        entity.setSecret(IdUtil.fastUUID());
        return entity;
    }
}
