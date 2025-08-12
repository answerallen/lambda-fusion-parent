package com.lambda.fusion.dict.controller;

import static com.lambda.fusion.core.utils.ParameterUtils.fuzzyQuery;
import static com.lambda.fusion.dict.common.constants.DictConstants.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.fusion.core.user.User;
import com.lambda.fusion.dict.common.enums.DictContextHolders;
import com.lambda.fusion.dict.common.enums.DictHolder;
import com.lambda.fusion.dict.dao.entity.DictInfo;
import com.lambda.fusion.dict.dao.entity.DictType;
import com.lambda.fusion.dict.dto.DictInfoQueryDTO;
import com.lambda.fusion.dict.dto.DictStateOperationDTO;
import com.lambda.fusion.dict.dto.QueryDictTree;
import com.lambda.fusion.dict.service.DictInfoService;
import com.lambda.fusion.dict.service.DictTypeService;
import com.lambda.fusion.dict.vo.DictInfoVO;
import com.lambda.fusion.dict.vo.DictTypeVo;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 多级数据字典类型相关接口
 *
 * @author Jin
 */
@RestController
@RequestMapping({"/dict"})
@Tag(name = "数据字典相关")
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class DictController {
    private final DictTypeService dictTypeService;
    private final DictInfoService dictInfoService;

    @OperationLog
    @GetMapping("/dicttypeall")
    @Operation(summary = "获取所有字典分类", description = "可以根据字典类型查询")
    public List<DictType> getDictTypeList(
            @Parameter(description = "字典类型") @RequestParam(required = false) String type) {
        return dictTypeService.getDictTypeList(type);
    }

    @GetMapping("/dictinfoall/dynamic")
    @Operation(summary = "获取所有启用的动态字典", description = "获取所有启用的字典")
    public Map<String, DictTypeVo> getAllDynamicDictInfo(
            @Parameter(description = "字典类型") @RequestParam(required = false) String type) {
        return dictInfoService.getDynamicDictInfoGroup(type);
    }

    @GetMapping("/dict/tree/dynamic")
    @Operation(summary = "查询树形结构的字典类型(动态字典)包含上下级节点", description = "查询树形结构的字典类型")
    public List<DictType> dynamicTree(@Parameter QueryDictTree queryDictTree) {
        return dictTypeService.dynamicTreeList(queryDictTree);
    }

    @GetMapping("/dict/tree/data/{type}")
    @Operation(summary = "查询树形结构的数据项", description = "根据字典类型查询树形结构数据项")
    public List<DictInfo> treeData(@Parameter(description = "字典类型") @PathVariable(required = false) String type) {
        return dictInfoService.getTreeData(type);
    }

    @GetMapping("/dict/tree/subdata/{type}")
    @Operation(summary = "根据数据类型查询包含子集数据类型的数据项", description = "根据数据类型查询包含子集数据类型的数据项")
    public List<DictInfo> subTreeData(@Parameter(description = "字典类型") @PathVariable(required = false) String type) {
        return dictInfoService.getSubTreeData(type);
    }

    @OperationLog
    @PostMapping("/dicttype")
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

    @OperationLog
    @GetMapping("/dictinfo/tree/data/{parentid}")
    @Operation(summary = "根据数据项父节点查询数据项树", description = "根据数据项父节点查询数据项树")
    public List<DictInfo> queryDictInfoByParentId(
            @Parameter(description = "数据项父ID", required = true) @PathVariable String parentid) {
        return dictInfoService.getDictInfoByParentId(parentid);
    }

    @OperationLog
    @PutMapping("/dicttype")
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

    @OperationLog
    @DeleteMapping("/dicttype/{id}")
    @Operation(summary = "删除字典类型信息", description = "删除字典类型信息")
    public void deleteDictType(@Parameter(description = "字典类型编号", required = true) @PathVariable String id) {
        dictTypeService.deleteDictType(id);
    }

    /**
     * 字典类型分页查询
     */
    @GetMapping({"/dicttype/page/{number:\\d+}", "/dicttype/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(
            summary = "字典类型分页查询",
            description = "字典类型分页查询",
            parameters = {
                @Parameter(
                        name = "number",
                        description = "当前页码",
                        in = ParameterIn.PATH,
                        schema = @Schema(defaultValue = "1")),
                @Parameter(
                        name = "size",
                        description = "每页条数",
                        in = ParameterIn.PATH,
                        schema = @Schema(defaultValue = DEFAULT_PAGE_SIZE)),
                @Parameter(name = "dictName", description = "字典名称", in = ParameterIn.QUERY)
            })
    public Page<DictType> dictTypeList(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            String dictName) {
        Map<String, String> parameters = new HashMap<>(1);
        if (StringUtils.isNotBlank(dictName)) {
            parameters.put("dictName", fuzzyQuery(dictName));
        }
        return dictTypeService.queryDictTypePage(new Page<>(number, size), parameters);
    }

    @GetMapping({"/dictinfo/page/{number:\\d+}", "/dictinfo/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(
            summary = "分页查询所有数据列表",
            description = "分页查询所有数据列表",
            parameters = {
                @Parameter(
                        name = "number",
                        description = "当前页码",
                        in = ParameterIn.PATH,
                        schema = @Schema(defaultValue = "1")),
                @Parameter(
                        name = "size",
                        description = "每页条数",
                        in = ParameterIn.PATH,
                        schema = @Schema(defaultValue = "20")),
                @Parameter(name = "dictType", description = "字典类型", in = ParameterIn.QUERY),
                @Parameter(name = "fieldType", description = "字段类型", in = ParameterIn.QUERY),
                @Parameter(name = "dictInfoId", description = "数据项Id", in = ParameterIn.QUERY),
                @Parameter(name = "fieldName", description = "字段名称", in = ParameterIn.QUERY),
                @Parameter(name = "enableState", description = "启用状态", in = ParameterIn.QUERY)
            })
    public Page<DictInfo> pageDictInfo(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            DictInfoQueryDTO dictInfoQueryDTO) {
        return dictInfoService.page(new Page<>(number, size), dictInfoQueryDTO);
    }

    @GetMapping("/dictinfo/data/select")
    @Operation(summary = "数据项条件查询", description = "分页查询所有数据列表")
    public List<DictInfo> selectDictInfo(DictInfoQueryDTO dictInfoQueryDTO) {
        return dictInfoService.selectDictInfo(dictInfoQueryDTO);
    }

    @OperationLog
    @PostMapping("/dictinfo")
    @Operation(summary = "添加字典详细信息", description = "添加字典详细信息")
    public DictInfo saveDictInfo(@Valid @RequestBody DictInfo dictInfo) {
        User operator = (User) OperatorUtils.getOperator();
        if (StringUtils.isNotBlank(operator.getTenantId())) {
            dictInfo.setTenantId(operator.getTenantId());
        }
        return dictInfoService.saveDictInfo(operator, dictInfo);
    }

    @OperationLog
    @PutMapping("/dictinfo")
    @Operation(summary = "更新字典详细信息", description = "更新字典详细信息")
    public DictInfo updateDictInfo(
            @Valid DictInfoVO dictInfoVO, @RequestBody(required = false) DictInfo.Additional additional) {
        DictInfo dictInfo = new DictInfo();
        BeanUtils.copyProperties(dictInfoVO, dictInfo);
        if (null != additional) {
            dictInfo.setParameters(additional.getParameters());
        }
        return dictInfoService.updateDictInfo(dictInfo);
    }

    @OperationLog
    @DeleteMapping("/dictinfo/{id}")
    @Operation(summary = "删除字典详细信息", description = "删除字典详细信息")
    public void deleteDictInfo(@Parameter(description = "字典类型编号", required = true) @PathVariable String id) {
        dictInfoService.deleteDictInfoById(id);
    }

    @PutMapping("/dictinfo/{id}/enable")
    @Operation(summary = "启用字典", description = "启用字典")
    public void changeEnable(@PathVariable("id") @Parameter(required = true, description = "字典详细信息Id") String id) {
        dictInfoService.updateEnableState(DictStateOperationDTO.builder()
                .id(id)
                .state(ENABLE_STATE_ENABLED)
                .build());
    }

    @PutMapping("/dictinfo/{id}/disable")
    @Operation(summary = "禁用字典", description = "禁用字典")
    public void changeDisable(@PathVariable("id") @Parameter(required = true, description = "字典详细信息Id") String id) {
        dictInfoService.updateEnableState(DictStateOperationDTO.builder()
                .id(id)
                .state(ENABLE_STATE_DISABLED)
                .build());
    }

    @PutMapping("/dictinfo/{id}/selectable")
    @Operation(summary = "设置可选择", description = "设置可选择")
    public void changeSelectable(@PathVariable("id") @Parameter(required = true, description = "字典详细信息Id") String id) {
        dictInfoService.updateSelectableState(DictStateOperationDTO.builder()
                .id(id)
                .state(SELECTABLE_STATE_ENABLED)
                .build());
    }

    @PutMapping("/dictinfo/{id}/unselectable")
    @Operation(summary = "设置不可选择", description = "设置不可选择")
    public void changeUnselectable(
            @PathVariable("id") @Parameter(required = true, description = "字典详细信息Id") String id) {
        dictInfoService.updateSelectableState(DictStateOperationDTO.builder()
                .id(id)
                .state(SELECTABLE_STATE_DISABLED)
                .build());
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
