package com.lambda.fusion.dict.model.dto;

import com.lambda.fusion.core.base.page.PageQueryDTO;
import com.lambda.fusion.dict.model.entity.DictInfo;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典信息分页查询DTO
 *
 * <p>继承PageQueryDTO基类，提供统一的分页查询功能，支持按字典类型、字段类型、字段名称等条件查询。
 *
 * <h3>功能特性：</h3>
 * <ul>
 * <li>支持字典类型精确查询</li>
 * <li>支持字段类型精确查询</li>
 * <li>支持字段名称模糊查询</li>
 * <li>支持父级ID精确查询</li>
 * <li>支持启用状态筛选</li>
 * <li>支持可选择状态筛选</li>
 * <li>参数校验和长度限制</li>
 * </ul>
 *
 * @author Generated
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "字典信息分页查询参数")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class DictInfoPageQueryDTO extends PageQueryDTO<DictInfo> {

    /**
     * 字典类型
     */
    @Schema(description = "字典类型")
    @Size(max = 100, message = "字典类型长度不能超过100个字符")
    private String dictType;

    /**
     * 字段类型
     */
    @Schema(description = "字段类型")
    @Size(max = 50, message = "字段类型长度不能超过50个字符")
    private String fieldType;

    /**
     * 字段名称
     */
    @Schema(description = "字段名称")
    @Size(max = 100, message = "字段名称长度不能超过100个字符")
    private String fieldName;

    /**
     * 父级ID
     */
    @Schema(description = "父级ID")
    @Size(max = 64, message = "父级ID长度不能超过64个字符")
    private String parentId;

    /**
     * 启用状态
     */
    @Schema(description = "启用状态，0：禁用，1：启用")
    private Integer enableState;

    /**
     * 可选择状态
     */
    @Schema(description = "可选择状态，0：不可选择，1：可选择")
    private Integer selectable;

    /**
     * 字典信息ID
     */
    @Schema(description = "字典信息ID")
    @Size(max = 64, message = "字典信息ID长度不能超过64个字符")
    private String dictInfoId;

    /**
     * 转换为原有的查询DTO
     *
     * @return DictInfoQueryDTO
     */
    public DictInfoQueryDTO toDictInfoQueryDTO() {
        return DictInfoQueryDTO.builder()
                .dictType(this.dictType)
                .fieldType(this.fieldType)
                .fieldName(this.fieldName)
                .parentId(this.parentId)
                .enableState(this.enableState)
                .selectable(this.selectable)
                .dictInfoId(this.dictInfoId)
                .build();
    }
}