package com.lambda.fusion.upload.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.upload.model.AttachmentGroupEntity;
import com.lambda.fusion.upload.model.AttachmentQuery;
import com.lambda.fusion.upload.model.AttachmentView;
import com.lambda.fusion.upload.model.UpsertAttachmentGroup;
import com.lambda.fusion.upload.service.AttachmentService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@SuppressFBWarnings("EI_EXPOSE_REP2")
@RestController
@Tag(name = "附件管理")
@RequestMapping("/upload/attachments")
@RequiredArgsConstructor
public class AttachmentController {
    private final AttachmentService attachmentService;

    @SaCheckPermission(value = "oss:attachment:upload")
    @PostMapping("/{groupId}/upload")
    @Operation(summary = "上传附件")
    public AttachmentView upload(
            @RequestPart("file") MultipartFile file,
            @PathVariable @Parameter(description = "分组编号") String groupId,
            @RequestParam(defaultValue = "default") @Parameter(description = "OSS客户端名称") String clientName) {
        return attachmentService.upload(file, groupId, clientName);
    }

    @SaCheckPermission(value = "oss:attachment:delete")
    @DeleteMapping("/{id}")
    @Operation(summary = "删除附件")
    public void delete(@PathVariable @Parameter(description = "附件编号") String id) {
        attachmentService.delete(id);
    }

    @SaCheckPermission(value = "oss:attachment:get")
    @GetMapping("/{id}")
    @Operation(summary = "查询附件")
    public AttachmentView detail(@PathVariable @Parameter(description = "附件编号") String id) {
        return attachmentService.getById(id);
    }

    @SaCheckPermission(value = "oss:attachment:preview-url")
    @GetMapping("/{id}/preview-url")
    @Operation(summary = "获取附件预签名访问地址")
    public String previewUrl(
            @PathVariable @Parameter(description = "附件编号") String id,
            @RequestParam(required = false) @Parameter(description = "有效时长(秒)") Integer expirationSeconds) {
        return attachmentService.previewUrl(id, expirationSeconds);
    }

    @SaCheckPermission(value = "oss:attachment:page")
    @GetMapping({"/page", "/page/{number:\\d+}", "/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(summary = "分页查询附件")
    public Page<AttachmentView> page(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            AttachmentQuery query) {
        return attachmentService.page(number, size, query);
    }

    @SaCheckPermission(value = "oss:attachment:client-list")
    @GetMapping("/clients")
    @Operation(summary = "查询可用OSS客户端")
    public List<String> listClients() {
        return attachmentService.listClientNames();
    }

    @SaCheckPermission(value = "oss:attachment-group:list")
    @GetMapping("/groups")
    @Operation(summary = "查询分组列表")
    public List<AttachmentGroupEntity> listGroups() {
        return attachmentService.listGroups();
    }

    @SaCheckPermission(value = "oss:attachment-group:add")
    @PostMapping("/groups")
    @Operation(summary = "新增分组")
    public AttachmentGroupEntity createGroup(@RequestBody @Valid UpsertAttachmentGroup source) {
        return attachmentService.createGroup(source);
    }

    @SaCheckPermission(value = "oss:attachment-group:update")
    @PutMapping("/groups/{id}")
    @Operation(summary = "更新分组")
    public AttachmentGroupEntity updateGroup(
            @PathVariable @Parameter(description = "分组编号") String id,
            @RequestBody @Valid UpsertAttachmentGroup source) {
        return attachmentService.updateGroup(id, source);
    }

    @SaCheckPermission(value = "oss:attachment-group:delete")
    @DeleteMapping("/groups/{id}")
    @Operation(summary = "删除分组")
    public void deleteGroup(@PathVariable @Parameter(description = "分组编号") String id) {
        attachmentService.deleteGroup(id);
    }
}
