package com.lambda.fusion.dict.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.core.user.User;
import com.lambda.fusion.dict.common.enums.DictContextHolders;
import com.lambda.fusion.dict.common.enums.DictHolder;
import com.lambda.fusion.dict.model.dto.*;
import com.lambda.fusion.dict.model.entity.DictType;
import com.lambda.fusion.dict.service.DictTypeService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 多级数据字典类型相关接口
 *
 * @author Jin
 */
@RestController
@RequestMapping({"/dict/Type"})
@Tag(name = "数据字典相关")
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class DictTypeController {
    private final DictTypeService dictTypeService;

    @GetMapping("/")
    @Operation(summary = "获取所有字典分类", description = "可以根据字典类型查询")
    public List<DictType> getDictTypeList(
            @Parameter(description = "字典类型") @RequestParam(required = false) String type) {
        return dictTypeService.getDictTypeList(type);
    }

    @PostMapping
    @Operation(summary = "添加字典类型", description = "添加字典类型")
    public DictType saveDictType(
            @Valid @Parameter(description = "字典类型数据", required = true) @RequestBody DictType dictType) {
        // 只有开发者才能指定字典用途，其他用户只能添加用户字典
        User operator = OperatorUtils.getLoginUser(User.class);
        if (operator.isDev()) {
            if (dictType.getDictUsage() == null) {
                dictType.setDictUsage(DictType.DictUsage.SYSTEM.getValue());
            }
        } else {
            dictType.setDictUsage(DictType.DictUsage.USER.getValue());
        }
        return dictTypeService.saveDictType(dictType);
    }

    @PutMapping
    @Operation(
            summary = "更新字典类型",
            description = "更新字典类型",
            parameters = {
                    @Parameter(name = "id", description = "id", required = true, in = ParameterIn.QUERY),
                    @Parameter(name = "dictName", description = "字典名称", required = true, in = ParameterIn.QUERY)
            })
    public DictType updateDictType(@Valid DictType dictType) {
        // 非开发者不能修改系统字典用途
        User operator = OperatorUtils.getLoginUser(User.class);
        if (!operator.isDev()) {
            DictType source = dictTypeService.getById(dictType.getId());
            if (source != null) {
                dictType.setDictUsage(source.getDictUsage());
            }
        }
        dictTypeService.updateDictType(dictType);
        return dictTypeService.getById(dictType.getId());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除字典类型信息", description = "删除字典类型信息")
    public void deleteDictType(@Parameter(description = "字典类型编号", required = true) @PathVariable String id) {
        dictTypeService.deleteDictType(id);
    }

    @GetMapping({"/page","/page/{number:\\d+}", "/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(
            summary = "字典类型分页查询",
            description = "字典类型分页查询，支持多条件查询和排序")
    public Page<DictType> dictTypeList(@PathVariable(required = false) Integer number,
                                       @PathVariable(required = false) Integer size,
                                       @Valid DictTypePageQueryDTO pageQueryDTO) {
        if(number!= null){
            pageQueryDTO.setPageNum(number);
        }
        if(size!= null){
            pageQueryDTO.setPageSize(size);
        }
        return dictTypeService.page(pageQueryDTO.getPage(), pageQueryDTO.getLambdaQueryWrapper());
    }


    @GetMapping("/tree/dynamic")
    @Operation(summary = "查询树形结构的字典类型(动态字典)包含上下级节点", description = "查询树形结构的字典类型")
    public List<DictType> dynamicTree(@Parameter QueryDictTree queryDictTree) {
        return dictTypeService.dynamicTreeList(queryDictTree);
    }

    @GetMapping("/dict/dynamic")
    @Operation(summary = "动态字典查询", description = "动态字典查询")
    public DictType dynamicDict(@Parameter(required = true, description = "字典类型ID") @RequestParam String dictTypeId) {
        return dictTypeService.dynamicDict(dictTypeId);
    }

    @GetMapping("/dict/enum")
    @Operation(summary = "获取所有枚举字典", description = "获取所有枚举字典")
    public Map<String, DictHolder> getAllEnumDict() {
        return DictContextHolders.getMapperHolders();
    }
}
