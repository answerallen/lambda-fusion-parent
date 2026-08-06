package com.lambda.fusion.ai.chat.service;

import com.lambda.fusion.ai.chat.model.entity.ChatAttachmentEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 对话附件服务：上传(校验+存储+插行)、按消息/会话查询、下载、删除与消息绑定。
 *
 * <p>所有权的最终判定收口在 {@link #loadOwned(String)}：附件经 session_id 关联到会话，
 * 由 {@code ChatSessionService.loadOwned} 完成 tenant + user 双层校验，防止同租户用户互访附件。
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

    /** 下载原文件到输出流（按行记录的 storageType 路由）。 */
    void download(String attachmentId, OutputStream out);

    /** 删除未发送附件（message_id IS NULL）；已发送附件随会话级联删除，不提供单删。 */
    void delete(String attachmentId);

    /** 级联删除会话全部附件（文件 + 行）；文件删除失败仅告警不阻断。 */
    void deleteBySession(String sessionId);
}
