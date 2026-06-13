package com.lambda.fusion.dict.model;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.fusion.core.pagination.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型分页查询DTO
 *
 * <p>继承PageQueryDTO基类，提供统一的分页查询功能，支持按字典名称等条件查询。
 *
 * <h3>功能特性：</h3>
 * <ul>
 * <li>支持字典名称模糊查询</li>
 * <li>支持字典编码精确查询</li>
 * <li>支持字典用途筛选</li>
 * <li>参数校验和长度限制</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "字典类型分页查询参数")
public class QueryDictTypePage extends PageQuery<DictTypeTree> {

    /**
     * 字典名称
     */
    @Schema(description = "字典名称，支持模糊查询")
    @Size(max = 100, message = "字典名称长度不能超过100个字符")
    private String dictName;

    @Override
    public LambdaQueryWrapper<DictTypeTree> getLambdaQueryWrapper() {
        LambdaQueryWrapper<DictTypeTree> lambdaQueryWrapper = super.getLambdaQueryWrapper();
        lambdaQueryWrapper.like(StrUtil.isNotBlank(dictName), DictTypeTree::getDictName, "%" + dictName + "%");
        lambdaQueryWrapper.orderByDesc(DictTypeTree::getDictType);
        return lambdaQueryWrapper;
    }
}
