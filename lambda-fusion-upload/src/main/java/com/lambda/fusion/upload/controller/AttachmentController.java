package com.lambda.fusion.upload.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.upload.model.AttachmentGroupEntity;
import com.lambda.fusion.upload.model.AttachmentQuery;
import com.lambda.fusion.upload.model.AttachmentView;
import com.lambda.fusion.upload.model.UpsertAttachmentGroup;
import com.lambda.fusion.upload.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "附件管理")
@RequestMapping("/upload/attachments")
@SaCheckRole(
        value = {FusionConstants.ROLE_ADMIN, FusionConstants.ROLE_SYSTEM, FusionConstants.ROLE_DEV},
        mode = SaMode.OR)
@RequiredArgsConstructor
public class AttachmentController {
    private final AttachmentService attachmentService;

    @PostMapping
    @Operation(summary = "上传附件")
    public AttachmentView upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) @Parameter(description = "分组编号") String groupId,
            @RequestParam(required = false) @Parameter(description = "OSS客户端名称") String clientName) {
        return attachmentService.upload(file, groupId, clientName);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除附件")
    public void delete(@PathVariable @Parameter(description = "附件编号") String id) {
        attachmentService.delete(id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询附件")
    public AttachmentView detail(@PathVariable @Parameter(description = "附件编号") String id) {
        return attachmentService.getById(id);
    }

    @GetMapping("/{id}/preview-url")
    @Operation(summary = "获取附件预签名访问地址")
    public String previewUrl(
            @PathVariable @Parameter(description = "附件编号") String id,
            @RequestParam(required = false) @Parameter(description = "有效时长(秒)") Integer expirationSeconds) {
        return attachmentService.previewUrl(id, expirationSeconds);
    }

    @GetMapping({"/page", "/page/{number:\\d+}", "/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(summary = "分页查询附件")
    public Page<AttachmentView> page(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            AttachmentQuery query) {
        return attachmentService.page(number, size, query);
    }

    @PatchMapping("/{id}/group/{groupId}")
    @Operation(summary = "变更附件分组")
    public void changeGroup(
            @PathVariable @Parameter(description = "附件编号") String id,
            @PathVariable @Parameter(description = "分组编号") String groupId) {
        attachmentService.changeGroup(id, groupId);
    }

    @GetMapping("/groups")
    @Operation(summary = "查询分组列表")
    public List<AttachmentGroupEntity> listGroups() {
        return attachmentService.listGroups();
    }

    @PostMapping("/groups")
    @Operation(summary = "新增分组")
    public AttachmentGroupEntity createGroup(@RequestBody @Valid UpsertAttachmentGroup source) {
        return attachmentService.createGroup(source);
    }

    @PutMapping("/groups/{id}")
    @Operation(summary = "更新分组")
    public AttachmentGroupEntity updateGroup(
            @PathVariable @Parameter(description = "分组编号") String id,
            @RequestBody @Valid UpsertAttachmentGroup source) {
        return attachmentService.updateGroup(id, source);
    }

    @DeleteMapping("/groups/{id}")
    @Operation(summary = "删除分组")
    public void deleteGroup(@PathVariable @Parameter(description = "分组编号") String id) {
        attachmentService.deleteGroup(id);
    }
}
