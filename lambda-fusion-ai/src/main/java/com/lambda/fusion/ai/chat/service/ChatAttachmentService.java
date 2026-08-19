package com.lambda.fusion.ai.chat.service;

import com.lambda.fusion.ai.chat.model.ChatAttachmentView;
import com.lambda.fusion.ai.chat.model.entity.ChatAttachmentEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 对话附件服务：上传(校验+存储+插行)、按消息/会话查询、下载、删除与消息绑定。附件经 session_id 关联会话，
 * 由现有租户插件和会话 user_id 所有权校验共同限制访问，防同租户用户互访。
 *
 * @author Jin
 */
public interface ChatAttachmentService {

    /**
     * 上传附件：校验会话所有权与类型/大小白名单，持久化原文件并插入附件行（message_id 为 NULL，
     * 待发送时 {@link #bindToMessage} 回填）。存储失败删除已插行后抛错。
     */
    ChatAttachmentEntity upload(String sessionId, MultipartFile file);

    /**
     * 校验附件归属（经 session 间接归属当前用户），不存在抛 {@code ATTACHMENT_NOT_FOUND}。
     */
    ChatAttachmentEntity loadOwned(String attachmentId);

    /**
     * 把已上传附件绑定到消息：逐条校验归属本会话且未被占用，回填 message_id。
     * 任一附件不存在/跨会话/已被占用即整体抛 {@code ATTACHMENT_NOT_FOUND}，发送失败。
     */
    List<ChatAttachmentEntity> bindToMessage(ChatSessionEntity session, List<String> attachmentIds, Long messageId);

    /** 按消息 ID 批量查询附件（历史消息渲染用）。 */
    List<ChatAttachmentEntity> listByMessageIds(Collection<Long> messageIds);

    /**
     * 转换为视图：图片附件填签名预览直链，收口 storageType/storagePath/tenantId 等内部字段。
     */
    ChatAttachmentView toView(ChatAttachmentEntity entity);

    /** 下载原文件到输出流（按行记录的 storageType 路由）。 */
    void download(String attachmentId, OutputStream out);

    /**
     * 仅供 preview 端点：按 id 查询（无 tenant/user 归属校验，调用方须自行鉴权）。
     * preview 端点放行 Bearer，无登录上下文，故不依赖 {@link #loadOwned}；鉴权由签名 token 保证。
     */
    ChatAttachmentEntity loadByIdForPreview(String attachmentId);

    /** 按实体的 storageType 路由存储下载到输出流（不查库、不校验归属，供 download 与 preview 复用）。 */
    void writeTo(ChatAttachmentEntity entity, OutputStream out);

    /** 删除未发送附件（message_id IS NULL）；已发送附件随会话级联删除，不提供单删。 */
    void delete(String attachmentId);

    /** 级联删除会话全部附件（文件 + 行）；文件删除失败仅告警不阻断。 */
    void deleteBySession(String sessionId);
}
