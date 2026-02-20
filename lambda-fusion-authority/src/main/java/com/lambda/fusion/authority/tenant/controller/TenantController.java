package com.lambda.fusion.authority.tenant.controller;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Maps;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.tenant.model.Tenant;
import com.lambda.fusion.authority.tenant.model.TenantEntity;
import com.lambda.fusion.authority.tenant.model.TenantOption;
import com.lambda.fusion.authority.tenant.model.TenantQuery;
import com.lambda.fusion.authority.tenant.service.TenantService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 租户信息表相关接口
 */
@Slf4j
@RestController
@RequestMapping("/authority/tenant")
@Tag(name = "租户管理")
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP")
public class TenantController {

    private final TenantService tenantService;

    @PostMapping("/page")
    @Operation(summary = "分页查询所有租户数据列表（V2版本）", description = "使用LambdaQueryWrapper进行分页查询，支持更灵活的排序和查询条件")
    public Page<TenantEntity> pageV2(@RequestBody TenantQuery queryDTO) {
        return tenantService.page(queryDTO.getPage(), queryDTO.getLambdaQueryWrapper());
    }

    /**
     * 构建查询参数
     *
     * @param queryDTO 查询DTO
     * @return 查询参数Map
     */
    private Map<String, Object> buildQueryParameters(TenantQuery queryDTO) {
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(10);
        if (StringUtils.isNotBlank(queryDTO.getTenantName())) {
            parameters.put("tenantName", queryDTO.getTenantName());
        }
        if (StringUtils.isNotBlank(queryDTO.getTenantAddress())) {
            parameters.put("tenantAddress", queryDTO.getTenantAddress());
        }
        if (StringUtils.isNotBlank(queryDTO.getLegalPerson())) {
            parameters.put("legalPerson", queryDTO.getLegalPerson());
        }
        if (StringUtils.isNotBlank(queryDTO.getTenantCode())) {
            parameters.put("tenantCode", queryDTO.getTenantCode());
        }
        if (StringUtils.isNotBlank(queryDTO.getLiaisonMan())) {
            parameters.put("liaisonMan", queryDTO.getLiaisonMan());
        }
        if (StringUtils.isNotBlank(queryDTO.getLiaisonPhone())) {
            parameters.put("liaisonPhone", queryDTO.getLiaisonPhone());
        }
        if (queryDTO.getEnabled() != null) {
            parameters.put("enabled", queryDTO.getEnabled());
        }
        if (queryDTO.getExamineState() != null) {
            parameters.put("examineState", queryDTO.getExamineState());
        }
        if (StringUtils.isNotBlank(queryDTO.getOwner())) {
            parameters.put("owner", queryDTO.getOwner());
        }
        if (StringUtils.isNotBlank(queryDTO.getAlias())) {
            parameters.put("alias", queryDTO.getAlias());
        }
        if (StringUtils.isNotBlank(queryDTO.getPrefecture())) {
            parameters.put("prefecture", queryDTO.getPrefecture());
        }
        return parameters;
    }

    @GetMapping("/options")
    @Operation(summary = "获取租户下拉列表", description = "查询租户下拉列表")
    public List<TenantOption> tenantOptions() {
        return tenantService.getTenantOptions();
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据编号查询租户信息信息", description = "根据id查询租户信息信息")
    public TenantEntity get(@Parameter(description = "租户信息编号", required = true) @PathVariable String id) {
        return tenantService.getById(id);
    }

    @PostMapping
    @Operation(summary = "新增租户信息信息", description = "新增租户信息信息")
    public TenantEntity save(
            MultipartFile tenantLogo, @Parameter(description = "租户信息信息", required = true) @RequestBody Tenant entity) {
        LoginUser operator = OperatorUtils.getOperator();
        TenantEntity target = new TenantEntity();
        BeanUtils.copyProperties(entity, target);
        String id = IdWorker.getIdStr();
        String tenantId = operator.getTenantId();
        target.setOwner(tenantId);
        target.setTenantId(id);
        if (tenantLogo != null) {
            log.info("file: {}", tenantLogo);
        }
        tenantService.save(target);
        return tenantService.getById(target.getTenantId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新租户信息信息", description = "更新租户信息信息")
    public TenantEntity update(
            MultipartFile tenantLogo,
            @Parameter(description = "租户信息编号", required = true) @PathVariable String id,
            @Parameter(description = "租户信息信息", required = true) @RequestBody Tenant tenant) {
        LoginUser operator = OperatorUtils.getOperator();
        TenantEntity target = new TenantEntity();
        BeanUtils.copyProperties(tenant, target);
        target.setTenantId(id);
        target.setUpdatedBy(operator.getName());
        if (tenantLogo != null) {
            log.info("file: {}", tenantLogo);
        }
        tenantService.updateById(target);
        return tenantService.getById(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除租户信息信息", description = "删除租户信息信息")
    public void delete(@Parameter(description = "租户编号", required = true) @PathVariable String id) {
        LoginUser operator = OperatorUtils.getOperator();
        tenantService.deleteTenant(operator, id);
    }

    @PatchMapping("/{id}/enabled")
    @Operation(summary = "启用租户")
    public void enabled(@PathVariable @Parameter(description = "租户编号", required = true) String id) {
        LoginUser operator = OperatorUtils.getOperator();
        Assert.notNull(id, "租户编号不能为空");
        TenantEntity tenant = tenantService.getById(id);
        Assert.notNull(tenant, "租户不存在！");
        tenantService.prohibitTenant(operator, 1, id);
    }

    @PatchMapping("/{id}/disabled")
    @Operation(summary = "禁用租户")
    public void disabled(@PathVariable @Parameter(description = "租户编号", required = true) String id) {
        LoginUser operator = OperatorUtils.getOperator();
        Assert.notNull(id, "租户编号不能为空");
        TenantEntity tenant = tenantService.getById(id);
        Assert.notNull(tenant, "租户不存在！");
        tenantService.prohibitTenant(operator, 0, id);
    }

    @PatchMapping("/{id}/stop")
    @Operation(summary = "停用租户")
    public void stop(@PathVariable @Parameter(description = "租户编号", required = true) String id) {
        LoginUser operator = OperatorUtils.getOperator();
        Assert.notNull(id, "租户编号不能为空");
        TenantEntity tenant = tenantService.getById(id);
        Assert.notNull(tenant, "租户不存在！");
        tenantService.prohibitTenant(operator, -1, id);
    }

    @PatchMapping("/{id}/examine")
    @Operation(summary = "审核租户")
    public void examine(@PathVariable @Parameter(description = "租户编号", required = true) String id) {
        LoginUser operator = OperatorUtils.getOperator();
        Assert.notNull(id, "租户编号不能为空");
        TenantEntity tenant = tenantService.getById(id);
        Assert.notNull(tenant, "租户不存在！");
        tenantService.examineTenant(operator, 1, id);
    }

    @PatchMapping("/{id}/config")
    @Operation(summary = "修改租户配置")
    public void updateConfig(
            @PathVariable @Parameter(description = "租户编号", required = true) String id,
            @Parameter(description = "配置json字符串", required = true) @RequestBody Map<String, Object> configMap) {
        LoginUser operator = OperatorUtils.getOperator();
        Assert.notNull(id, "租户编号不能为空");
        TenantEntity tenant = tenantService.getById(id);
        Assert.notNull(tenant, "租户不存在！");
        log.debug("接收到参数：{}", configMap.toString());
        tenantService.updateConfig(operator, id, configMap);
    }

    @GetMapping("/{id}/config")
    @Operation(summary = "获取租户配置")
    public JsonNode getConfig(@PathVariable String id) {
        LoginUser operator = OperatorUtils.getOperator();
        return tenantService.getTenantConfigureById(operator, id);
    }

    @Operation(summary = "初始化租户的主库，需要先设置租户主库映射")
    @PostMapping("/{tenantId}/database/init")
    public void initTenantMainDataBase(@PathVariable String tenantId) {
        LoginUser operator = OperatorUtils.getOperator();
        tenantService.initTenantMainDataBase(tenantId, operator);
    }
}
