package com.lambda.fusion.authority.client.model.dto;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.lambda.cloud.core.base.BaseDTO;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.client.model.entity.ClientEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang.StringUtils;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.BeanUtils;

import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "客户端信息")
public class ClientInputDTO extends BaseDTO<ClientInputDTO, ClientEntity> {
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

    @Override
    public ClientEntity convertToEntity() {
        ClientEntity target = BeanUtil.copyProperties(this, ClientEntity.class);
        String tenantId = OperatorUtils.getOperator().getTenantId();
        if (StringUtils.isNotBlank(tenantId)) {
            target.setTenantId(tenantId);
        }
        target.setSecret(IdUtil.fastUUID());
        return target;
    }
}
