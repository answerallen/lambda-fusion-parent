package com.lambda.fusion.dict.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.core.identity.LoginUserDetails;
import com.lambda.fusion.core.utils.SecurityUtils;
import com.lambda.fusion.dict.model.DictTypeTree;
import com.lambda.fusion.dict.model.QueryDictTree;
import com.lambda.fusion.dict.model.QueryDictTypePage;
import com.lambda.fusion.dict.service.DictTypeService;
import com.lambda.fusion.dict.support.DictHolder;
import com.lambda.fusion.dict.support.DictUsage;
import com.lambda.fusion.dict.support.registry.DictRegistry;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 多级数据字典类型相关接口
 *
 * @author Jin
 */
@RestController
@RequestMapping({"/dictType"})
@Tag(name = "数据字典相关")
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class DictTypeController {
    private final DictTypeService dictTypeService;

    @GetMapping("/")
    @Operation(summary = "获取所有字典分类", description = "可以根据字典类型查询")
    public List<DictTypeTree> getDictTypeList(
            @Parameter(description = "字典类型") @RequestParam(required = false) String type) {
        return dictTypeService.getDictTypeList(type);
    }

    @PostMapping
    @Operation(summary = "添加字典类型", description = "添加字典类型")
    public DictTypeTree saveDictType(
            @Valid @Parameter(description = "字典类型数据", required = true) @RequestBody DictTypeTree dictTypeTree) {
        // 只有开发者才能指定字典用途，其他用户只能添加用户字典
        LoginUserDetails loginUserDetails = SecurityUtils.getUser();
        if (loginUserDetails.isDev()) {
            if (dictTypeTree.getDictUsage() == null) {
                dictTypeTree.setDictUsage(DictUsage.SYSTEM.getValue());
            }
        } else {
            dictTypeTree.setDictUsage(DictUsage.USER.getValue());
        }
        return dictTypeService.saveDictType(dictTypeTree);
    }

    @PutMapping
    @Operation(
            summary = "更新字典类型",
            description = "更新字典类型",
            parameters = {
                @Parameter(name = "id", description = "id", required = true, in = ParameterIn.QUERY),
                @Parameter(name = "dictName", description = "字典名称", required = true, in = ParameterIn.QUERY)
            })
    public DictTypeTree updateDictType(@Valid DictTypeTree dictTypeTree) {
        // 非开发者不能修改系统字典用途
        LoginUserDetails loginUserDetails = SecurityUtils.getUser();
        if (!loginUserDetails.isDev()) {
            DictTypeTree source = dictTypeService.getById(dictTypeTree.getId());
            if (source != null) {
                dictTypeTree.setDictUsage(source.getDictUsage());
            }
        }
        dictTypeService.updateDictType(dictTypeTree);
        return dictTypeService.getById(dictTypeTree.getId());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除字典类型信息", description = "删除字典类型信息")
    public void deleteDictType(@Parameter(description = "字典类型编号", required = true) @PathVariable String id) {
        dictTypeService.deleteDictType(id);
    }

    @GetMapping({"/page", "/page/{number:\\d+}", "/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(summary = "字典类型分页查询", description = "字典类型分页查询，支持多条件查询和排序")
    public Page<DictTypeTree> dictTypeList(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            @Valid QueryDictTypePage pageQueryDTO) {
        if (number != null) {
            pageQueryDTO.setPageNum(number);
        }
        if (size != null) {
            pageQueryDTO.setPageSize(size);
        }
        return dictTypeService.page(pageQueryDTO.getPage(), pageQueryDTO.getLambdaQueryWrapper());
    }

    @GetMapping("/tree/composite")
    @Operation(summary = "查询树形结构的字典类型(动态字典)包含上下级节点", description = "查询树形结构的字典类型")
    public List<DictTypeTree> compositeTree(@Parameter QueryDictTree queryDictTree) {
        return dictTypeService.compositeTreeList(queryDictTree);
    }

    @GetMapping("/dict/composite")
    @Operation(summary = "动态字典查询", description = "动态字典查询")
    public DictTypeTree compositeDict(
            @Parameter(required = true, description = "字典类型ID") @RequestParam String dictTypeId) {
        return dictTypeService.compositeDict(dictTypeId);
    }

    @GetMapping("/dict/enum")
    @Operation(summary = "获取所有枚举字典", description = "获取所有枚举字典")
    public Map<String, DictHolder> getAllEnumDict() {
        return DictRegistry.getMapperHolders();
    }
}
