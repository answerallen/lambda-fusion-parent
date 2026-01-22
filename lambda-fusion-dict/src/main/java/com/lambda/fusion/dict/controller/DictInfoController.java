package com.lambda.fusion.dict.controller;

import static com.lambda.fusion.dict.support.constants.DictConstants.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.fusion.core.identity.UserPrincipal;
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
import org.apache.commons.lang.StringUtils;
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

    @GetMapping({"/page", "/page/{number:\\d+}", "/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(summary = "字典类型分页查询", description = "字典类型分页查询，支持多条件查询和排序")
    public Page<DictInfo> dictTypeList(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            @Valid QueryDictInfoPage pageQueryDTO) {
        if (number != null) {
            pageQueryDTO.setPageNum(number);
        }
        if (size != null) {
            pageQueryDTO.setPageSize(size);
        }
        return dictInfoService.page(pageQueryDTO.getPage(), pageQueryDTO.toDictInfoQueryDTO());
    }

    @GetMapping("/dynamic")
    @Operation(summary = "获取所有启用的动态字典", description = "获取所有启用的字典")
    public Map<String, DictType> getAllDynamicDictInfo(
            @Parameter(description = "字典类型") @RequestParam(required = false) String type) {
        return dictInfoService.getDynamicDictInfoGroup(type);
    }

    @GetMapping("/dict/tree/data/{type}")
    @Operation(summary = "查询树形结构的数据项", description = "根据字典类型查询树形结构数据项")
    public List<DictInfo> treeData(@Parameter(description = "字典类型") @PathVariable(required = false) String type) {
        return dictInfoService.getTreeData(type);
    }

    @GetMapping("/dict/tree/subData/{type}")
    @Operation(summary = "根据数据类型查询包含子集数据类型的数据项", description = "根据数据类型查询包含子集数据类型的数据项")
    public List<DictInfo> subTreeData(@Parameter(description = "字典类型") @PathVariable(required = false) String type) {
        return dictInfoService.getSubTreeData(type);
    }

    @OperationLog
    @GetMapping("/tree/data/{parentid}")
    @Operation(summary = "根据数据项父节点查询数据项树", description = "根据数据项父节点查询数据项树")
    public List<DictInfo> queryDictInfoByParentId(
            @Parameter(description = "数据项父ID", required = true) @PathVariable String parentid) {
        return dictInfoService.getDictInfoByParentId(parentid);
    }

    @GetMapping("/data/select")
    @Operation(summary = "数据项条件查询", description = "分页查询所有数据列表")
    public List<DictInfo> selectDictInfo(QueryDictInfo queryDictInfo) {
        return dictInfoService.selectDictInfo(queryDictInfo);
    }

    @OperationLog
    @PostMapping
    @Operation(summary = "添加字典详细信息", description = "添加字典详细信息")
    public DictInfo saveDictInfo(@Valid @RequestBody DictInfo dictionaryEntry) {
        UserPrincipal userPrincipal = (UserPrincipal) OperatorUtils.getOperator();
        if (StringUtils.isNotBlank(userPrincipal.getTenantId())) {
            dictionaryEntry.setTenantId(userPrincipal.getTenantId());
        }
        return dictInfoService.saveDictInfo(userPrincipal, dictionaryEntry);
    }

    @OperationLog
    @PutMapping("/{id}")
    @Operation(summary = "更新字典详细信息", description = "更新字典详细信息")
    public void updateDictInfo(@PathVariable("id") String id, @Valid InputDictInfo inputDictInfo) {
        dictInfoService.updateDictInfo(id, inputDictInfo.toEntity());
    }

    @OperationLog
    @DeleteMapping("/{id}")
    @Operation(summary = "删除字典详细信息", description = "删除字典详细信息")
    public void deleteDictInfo(@Parameter(description = "字典类型编号", required = true) @PathVariable String id) {
        dictInfoService.deleteDictInfoById(id);
    }

    @PutMapping("/{id}/enable")
    @Operation(summary = "启用字典", description = "启用字典")
    public void changeEnable(@PathVariable("id") @Parameter(required = true, description = "字典详细信息Id") String id) {
        dictInfoService.updateEnableState(
                OperationDictState.builder().id(id).state(ENABLE_STATE_ENABLED).build());
    }

    @PutMapping("/{id}/disable")
    @Operation(summary = "禁用字典", description = "禁用字典")
    public void changeDisable(@PathVariable("id") @Parameter(required = true, description = "字典详细信息Id") String id) {
        dictInfoService.updateEnableState(
                OperationDictState.builder().id(id).state(ENABLE_STATE_DISABLED).build());
    }

    @PutMapping("/{id}/selectable")
    @Operation(summary = "设置可选择", description = "设置可选择")
    public void changeSelectable(@PathVariable("id") @Parameter(required = true, description = "字典详细信息Id") String id) {
        dictInfoService.updateSelectableState(OperationDictState.builder()
                .id(id)
                .state(SELECTABLE_STATE_ENABLED)
                .build());
    }

    @PutMapping("/{id}/unselectable")
    @Operation(summary = "设置不可选择", description = "设置不可选择")
    public void changeUnselectable(
            @PathVariable("id") @Parameter(required = true, description = "字典详细信息Id") String id) {
        dictInfoService.updateSelectableState(OperationDictState.builder()
                .id(id)
                .state(SELECTABLE_STATE_DISABLED)
                .build());
    }
}
