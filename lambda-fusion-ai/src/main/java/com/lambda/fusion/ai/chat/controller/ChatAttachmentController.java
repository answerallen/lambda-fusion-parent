package com.lambda.fusion.ai.chat.controller;

import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.fusion.ai.chat.model.entity.ChatAttachmentEntity;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "对话附件")
@RestController
@RequestMapping("/v1/ai/chat/attachments")
@RequiredArgsConstructor
public class ChatAttachmentController {

    private static final String CATEGORY_IMAGE = "IMAGE";

    private final ChatAttachmentService chatAttachmentService;

    @OperationLog
    @Operation(summary = "上传对话附件(图片/文档)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChatAttachmentEntity upload(
            @Parameter(description = "会话ID", required = true) @RequestParam("sessionId") String sessionId,
            @RequestParam("file") MultipartFile file) {
        return chatAttachmentService.upload(sessionId, file);
    }

    @OperationLog
    @Operation(summary = "删除未发送附件")
    @DeleteMapping("/{id}")
    public void delete(@Parameter(description = "附件ID", required = true) @PathVariable String id) {
        chatAttachmentService.delete(id);
    }

    @Operation(summary = "下载/预览附件原文件")
    @GetMapping("/{id}/download")
    public void download(
            @Parameter(description = "附件ID", required = true) @PathVariable String id, HttpServletResponse response)
            throws IOException {
        ChatAttachmentEntity attachment = chatAttachmentService.loadOwned(id);
        String fileName = StringUtils.defaultIfBlank(attachment.getFileName(), id);
        // RFC 5987：文件名 URL 编码（UTF-8），避免中文名乱码
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        // 图片用 inline + 原始 mime_type 供前端预览，文档用 attachment + octet-stream 强制下载
        if (CATEGORY_IMAGE.equals(attachment.getCategory()) && StringUtils.isNotBlank(attachment.getMimeType())) {
            response.setContentType(attachment.getMimeType());
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded);
        } else {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
        }
        chatAttachmentService.download(id, response.getOutputStream());
        response.flushBuffer();
    }
}
