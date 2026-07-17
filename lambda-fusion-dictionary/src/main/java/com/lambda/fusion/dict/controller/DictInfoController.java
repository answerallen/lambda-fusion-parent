package com.lambda.fusion.dict.controller;

import static com.lambda.fusion.dict.DictConstants.*;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lambda.fusion.core.identity.UserDetails;
import com.lambda.fusion.core.utils.AuthUtils;
import com.lambda.fusion.dict.model.*;
import com.lambda.fusion.dict.service.DictInfoService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 多级数据字典类型相关接口
 *
 * @author Jin
 */
@RestController
@RequestMapping({"/dictInfo"})
@Tag(name = "数据字典相关")
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class DictInfoController {
    private final DictInfoService dictInfoService;

    @SaCheckPermission(value = "dict:dict-info:page")
    @GetMapping({"/page", "/page/{number:\\d+}", "/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(summary = "字典类型分页查询", description = "字典类型分页查询，支持多条件查询和排序")
    public IPage<DictInfo> dictTypeList(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            @Valid QueryDictInfoPage pageQueryDTO) {
        if (number != null) {
            pageQueryDTO.setPageNum(number);
        }
        if (size != null) {
            pageQueryDTO.setPageSize(size);
        }
        return dictInfoService.page(pageQueryDTO);
    }

    @SaCheckPermission(value = "dict:dict-info:add")
    @PostMapping
    @Operation(summary = "添加字典详细信息", description = "添加字典详细信息")
    public DictInfo saveDictInfo(@Valid @RequestBody DictInfo dictInfo) {
        UserDetails userDetails = AuthUtils.getUser();
        if (StringUtils.isNotBlank(userDetails.getTenantId())) {
            dictInfo.setTenantId(userDetails.getTenantId());
        }
        return dictInfoService.saveDictInfo(userDetails, dictInfo);
    }

    @SaCheckPermission(value = "dict:dict-info:update")
    @PutMapping("/{id}")
    @Operation(summary = "更新字典详细信息", description = "更新字典详细信息")
    public void updateDictInfo(@PathVariable String id, @Valid InputDictInfo inputDictInfo) {
        dictInfoService.updateDictInfo(id, inputDictInfo.toEntity());
    }

    @SaCheckPermission(value = "dict:dict-info:delete")
    @DeleteMapping("/{id}")
    @Operation(summary = "删除字典详细信息", description = "删除字典详细信息")
    public void deleteDictInfo(@Parameter(description = "字典类型编号", required = true) @PathVariable String id) {
        dictInfoService.deleteDictInfoById(id);
    }

    @SaCheckPermission(value = "dict:dict-info:enable")
    @PutMapping("/{id}/enable")
    @Operation(summary = "启用字典", description = "启用字典")
    public void changeEnable(@PathVariable @Parameter(required = true, description = "字典详细信息Id") String id) {
        dictInfoService.updateEnableState(
                OperationDictState.builder().id(id).state(ENABLE_STATE_ENABLED).build());
    }

    @SaCheckPermission(value = "dict:dict-info:disable")
    @PutMapping("/{id}/disable")
    @Operation(summary = "禁用字典", description = "禁用字典")
    public void changeDisable(@PathVariable @Parameter(required = true, description = "字典详细信息Id") String id) {
        dictInfoService.updateEnableState(
                OperationDictState.builder().id(id).state(ENABLE_STATE_DISABLED).build());
    }

    @SaCheckPermission(value = "dict:dict-info:selectable")
    @PutMapping("/{id}/selectable")
    @Operation(summary = "设置可选择", description = "设置可选择")
    public void changeSelectable(@PathVariable @Parameter(required = true, description = "字典详细信息Id") String id) {
        dictInfoService.updateSelectableState(
                OperationDictState.builder().id(id).state(SELECTABLE_ENABLED).build());
    }

    @SaCheckPermission(value = "dict:dict-info:unselectable")
    @PutMapping("/{id}/unselectable")
    @Operation(summary = "设置不可选择", description = "设置不可选择")
    public void changeUnselectable(@PathVariable @Parameter(required = true, description = "字典详细信息Id") String id) {
        dictInfoService.updateSelectableState(
                OperationDictState.builder().id(id).state(SELECTABLE_DISABLED).build());
    }

    @SaCheckPermission(value = "dict:dict-info:composite-list")
    @GetMapping("/composite")
    @Operation(summary = "获取所有启用的动态字典", description = "获取所有启用的字典")
    public Map<String, DictType> getAllCompositeDictInfo(
            @Parameter(description = "字典类型") @RequestParam(required = false) String dictType) {
        return dictInfoService.getCompositeDictInfoGroup(dictType);
    }

    @SaCheckPermission(value = "dict:dict-info:tree")
    @GetMapping("/dict/tree/{dictType}")
    @Operation(summary = "查询树形结构的数据项", description = "根据字典类型查询树形结构数据项")
    public List<DictInfo> treeData(@Parameter(description = "字典类型") @PathVariable(required = false) String dictType) {
        return dictInfoService.getTreeData(dictType);
    }

    @SaCheckPermission(value = "dict:dict-info:sub-tree")
    @GetMapping("/dict/tree/{type}/data")
    @Operation(summary = "根据数据类型查询包含子集数据类型的数据项", description = "根据数据类型查询包含子集数据类型的数据项")
    public List<DictInfo> subTreeData(@Parameter(description = "字典类型") @PathVariable(required = false) String type) {
        return dictInfoService.getSubTreeData(type);
    }

    @SaCheckPermission(value = "dict:dict-info:tree-by-parent")
    @GetMapping("/tree/{parentId}")
    @Operation(summary = "根据数据项父节点查询数据项树", description = "根据数据项父节点查询数据项树")
    public List<DictInfo> queryDictInfoByParentId(
            @Parameter(description = "数据项父ID", required = true) @PathVariable String parentId) {
        return dictInfoService.getDictInfoByParentId(parentId);
    }

    @SaCheckPermission(value = "dict:dict-info:select")
    @GetMapping("/data/select")
    @Operation(summary = "数据项条件查询", description = "查询所有数据列表")
    public List<DictInfo> selectDictInfo(QueryDictInfo queryDictInfo) {
        return dictInfoService.selectDictInfo(queryDictInfo);
    }
}
