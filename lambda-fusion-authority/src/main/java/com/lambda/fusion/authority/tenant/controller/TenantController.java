package com.lambda.fusion.authority.tenant.controller;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Maps;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.tenant.model.TenantEntity;
import com.lambda.fusion.authority.tenant.model.TenantQuery;
import com.lambda.fusion.authority.tenant.model.TenantVO;
import com.lambda.fusion.authority.tenant.service.TenantService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @GetMapping({"/page/{number:\\d+}", "/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(
            summary = "分页查询所有租户数据列表",
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
                @Parameter(name = "tenantName", description = "租户名称", in = ParameterIn.QUERY),
                @Parameter(name = "tenantAddress", description = "地址", in = ParameterIn.QUERY),
                @Parameter(name = "legalPerson", description = "法人", in = ParameterIn.QUERY)
            })
    public Page<TenantEntity> page(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            @RequestParam(required = false) String tenantName,
            @RequestParam(required = false) String tenantAddress,
            @RequestParam(required = false) String legalPerson) {
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(5);
        if (StringUtils.isNotBlank(tenantName)) {
            parameters.put("tenantName", tenantName);
        }
        if (StringUtils.isNotBlank(tenantAddress)) {
            parameters.put("tenantAddress", tenantAddress);
        }
        if (StringUtils.isNotBlank(legalPerson)) {
            parameters.put("legalPerson", legalPerson);
        }
        return tenantService.page(new Page<>(number, size), parameters);
    }

    @GetMapping("/list")
    @Operation(summary = "获取租户下拉列表", description = "查询租户下拉列表")
    public List<TenantQuery> list() {
        return tenantService.getTenantList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据编号查询租户信息信息", description = "根据id查询租户信息信息")
    public TenantEntity get(@Parameter(description = "租户信息编号", required = true) @PathVariable String id) {
        return tenantService.getById(id);
    }

    @PostMapping
    @Operation(summary = "新增租户信息信息", description = "新增租户信息信息")
    public TenantEntity save(
            MultipartFile tenantLogo,
            @Parameter(description = "租户信息信息", required = true) @RequestBody TenantVO entity) {
        LoginUser operator = OperatorUtils.getOperator();
        TenantEntity target = new TenantEntity();
        BeanUtils.copyProperties(entity, target);
        String id = IdWorker.getIdStr();
        String tenantId = operator.getTenantId();
        target.setOwner(tenantId);
        target.setTenantId(id);
        if (CollectionUtils.isNotEmpty(entity.getPrefectureList())) {
            target.setPrefecture(StringUtils.join(entity.getPrefectureList(), ","));
        }
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
            @Parameter(description = "租户信息信息", required = true) @RequestBody TenantVO entity) {
        LoginUser operator = OperatorUtils.getOperator();
        TenantEntity target = new TenantEntity();
        BeanUtils.copyProperties(entity, target);
        target.setTenantId(id);
        target.setLastUpdateBy(operator.getUsername());
        if (CollectionUtils.isNotEmpty(entity.getPrefectureList())) {
            target.setPrefecture(StringUtils.join(entity.getPrefectureList(), ","));
        } else {
            target.setPrefecture("");
        }
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
    public void enabled(@Parameter(description = "租户编号", required = true) @PathVariable("id") String id) {
        LoginUser operator = OperatorUtils.getOperator();
        Assert.notNull(id, "租户编号不能为空");
        TenantEntity tenant = tenantService.getById(id);
        Assert.notNull(tenant, "租户不存在！");
        tenantService.prohibitTenant(operator, 1, id);
    }

    @PatchMapping("/{id}/disabled")
    @Operation(summary = "禁用租户")
    public void disabled(@Parameter(description = "租户编号", required = true) @PathVariable("id") String id) {
        LoginUser operator = OperatorUtils.getOperator();
        Assert.notNull(id, "租户编号不能为空");
        TenantEntity tenant = tenantService.getById(id);
        Assert.notNull(tenant, "租户不存在！");
        tenantService.prohibitTenant(operator, 0, id);
    }

    @PatchMapping("/{id}/stop")
    @Operation(summary = "停用租户")
    public void stop(@Parameter(description = "租户编号", required = true) @PathVariable("id") String id) {
        LoginUser operator = OperatorUtils.getOperator();
        Assert.notNull(id, "租户编号不能为空");
        TenantEntity tenant = tenantService.getById(id);
        Assert.notNull(tenant, "租户不存在！");
        tenantService.prohibitTenant(operator, -1, id);
    }

    @PatchMapping("/{id}/examine")
    @Operation(summary = "审核租户")
    public void examine(@Parameter(description = "租户编号", required = true) @PathVariable("id") String id) {
        LoginUser operator = OperatorUtils.getOperator();
        Assert.notNull(id, "租户编号不能为空");
        TenantEntity tenant = tenantService.getById(id);
        Assert.notNull(tenant, "租户不存在！");
        tenantService.examineTenant(operator, 1, id);
    }

    @PatchMapping("/{id}/config")
    @Operation(summary = "修改租户配置")
    public void updateConfig(
            @Parameter(description = "租户编号", required = true) @PathVariable("id") String id,
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
    public JsonNode getConfig(@PathVariable("id") String id) {
        LoginUser operator = OperatorUtils.getOperator();
        return tenantService.getTenantConfigureById(operator, id);
    }

    @Operation(summary = "初始化租户的主库，需要先设置租户主库映射")
    @PostMapping("/{tenantId}/database/init")
    public void initTenantMainDataBase(@PathVariable("tenantId") String tenantId) {
        LoginUser operator = OperatorUtils.getOperator();
        tenantService.initTenantMainDataBase(tenantId, operator);
    }
}
