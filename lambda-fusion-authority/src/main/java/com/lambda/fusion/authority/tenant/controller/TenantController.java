package com.lambda.fusion.authority.tenant.controller;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.cloud.oss.manager.OssClientManager;
import com.lambda.cloud.oss.model.UploadObjectResult;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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
    private final OssClientManager ossClientManager;
    private final ObjectMapper objectMapper;

    @PostMapping("/page")
    @Operation(summary = "分页查询所有租户数据列表（V2版本）", description = "使用LambdaQueryWrapper进行分页查询，支持更灵活的排序和查询条件")
    public Page<TenantEntity> pageTenant(@RequestBody TenantQuery queryDTO) {
        return tenantService.page(queryDTO.getPage(), queryDTO.getLambdaQueryWrapper());
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

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "新增租户信息信息", description = "新增租户信息信息")
    public TenantEntity save(@Parameter(description = "租户信息信息", required = true) @RequestBody Tenant tenant) {
        LoginUser operator = OperatorUtils.getOperator();
        TenantEntity target = tenant.toEntity();
        String id = IdWorker.getIdStr();
        String tenantId = operator.getTenantId();
        target.setOwner(tenantId);
        target.setTenantId(id);
        tenantService.save(target);
        return tenantService.getById(target.getTenantId());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "新增租户信息信息(含LOGO)", description = "新增租户信息信息，支持表单与LOGO同请求提交")
    public TenantEntity saveWithLogo(
            @Parameter(description = "租户信息信息", required = true) @RequestPart("tenant") String tenant,
            @Parameter(description = "LOGO文件") @RequestPart(value = "logo", required = false) MultipartFile logo,
            @Parameter(description = "OSS客户端名称（可选）") @RequestParam(value = "client", required = false)
                    String clientName) {
        LoginUser operator = OperatorUtils.getOperator();
        Tenant input = readTenant(tenant);
        TenantEntity target = input.toEntity();
        String id = IdWorker.getIdStr();
        String tenantId = operator.getTenantId();
        target.setOwner(tenantId);
        target.setTenantId(id);
        if (logo != null && !logo.isEmpty()) {
            target.setTenantLogo(uploadLogo(logo, clientName).getUrl());
        }
        tenantService.save(target);
        return tenantService.getById(target.getTenantId());
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "更新租户信息信息", description = "更新租户信息信息")
    public TenantEntity update(
            @Parameter(description = "租户信息编号", required = true) @PathVariable String id,
            @Parameter(description = "租户信息信息", required = true) @RequestBody Tenant tenant) {
        LoginUser operator = OperatorUtils.getOperator();
        TenantEntity target = tenant.toEntity();
        target.setTenantId(id);
        target.setUpdatedBy(operator.getName());
        tenantService.updateById(target);
        return tenantService.getById(id);
    }

    @PostMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "更新租户信息信息(含LOGO)", description = "更新租户信息信息，支持表单与LOGO同请求提交")
    public TenantEntity updateWithLogo(
            @Parameter(description = "租户信息编号", required = true) @PathVariable String id,
            @Parameter(description = "租户信息信息", required = true) @RequestPart("tenant") String tenant,
            @Parameter(description = "LOGO文件") @RequestPart(value = "logo", required = false) MultipartFile logo,
            @Parameter(description = "OSS客户端名称（可选）")
                    @RequestParam(value = "client", defaultValue = "default", required = false)
                    String clientName) {
        LoginUser operator = OperatorUtils.getOperator();
        Tenant input = readTenant(tenant);
        TenantEntity target = input.toEntity();
        target.setTenantId(id);
        target.setUpdatedBy(operator.getName());
        if (logo != null && !logo.isEmpty()) {
            target.setTenantLogo(uploadLogo(logo, clientName).getUrl());
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
        if (id == null) {
            throw AuthorityBusinessException.invalidParameter("租户编号不能为空");
        }
        TenantEntity tenant = tenantService.getById(id);
        if (tenant == null) {
            throw AuthorityBusinessException.tenantNotFound(id);
        }
        tenantService.prohibitTenant(operator, 1, id);
    }

    @PatchMapping("/{id}/disabled")
    @Operation(summary = "禁用租户")
    public void disabled(@PathVariable @Parameter(description = "租户编号", required = true) String id) {
        LoginUser operator = OperatorUtils.getOperator();
        if (id == null) {
            throw AuthorityBusinessException.invalidParameter("租户编号不能为空");
        }
        TenantEntity tenant = tenantService.getById(id);
        if (tenant == null) {
            throw AuthorityBusinessException.tenantNotFound(id);
        }
        tenantService.prohibitTenant(operator, 0, id);
    }

    @PatchMapping("/{id}/stop")
    @Operation(summary = "停用租户")
    public void stop(@PathVariable @Parameter(description = "租户编号", required = true) String id) {
        LoginUser operator = OperatorUtils.getOperator();
        if (id == null) {
            throw AuthorityBusinessException.invalidParameter("租户编号不能为空");
        }
        TenantEntity tenant = tenantService.getById(id);
        if (tenant == null) {
            throw AuthorityBusinessException.tenantNotFound(id);
        }
        tenantService.prohibitTenant(operator, -1, id);
    }

    @PatchMapping("/{id}/examine")
    @Operation(summary = "审核租户")
    public void examine(@PathVariable @Parameter(description = "租户编号", required = true) String id) {
        LoginUser operator = OperatorUtils.getOperator();
        if (id == null) {
            throw AuthorityBusinessException.invalidParameter("租户编号不能为空");
        }
        TenantEntity tenant = tenantService.getById(id);
        if (tenant == null) {
            throw AuthorityBusinessException.tenantNotFound(id);
        }
        tenantService.examineTenant(operator, 1, id);
    }

    private UploadObjectResult uploadLogo(MultipartFile file, String clientName) {
        try {
            if (file == null || file.isEmpty()) {
                throw AuthorityBusinessException.invalidParameter("文件不能为空");
            }
            String ext = FileUtil.extName(file.getOriginalFilename());
            String suffix = (ext != null && !ext.isBlank()) ? "." + ext : "";
            String objectKey = "tenant/logo/" + IdWorker.getIdStr() + suffix;
            return ossClientManager.get(clientName).upload(file.getInputStream(), objectKey, file.getContentType());
        } catch (Exception e) {
            throw AuthorityBusinessException.systemError("上传失败：" + e.getMessage());
        }
    }

    private Tenant readTenant(String payload) {
        try {
            return objectMapper.readValue(payload, Tenant.class);
        } catch (Exception e) {
            throw AuthorityBusinessException.invalidParameter("tenant解析失败：" + e.getMessage());
        }
    }
}
