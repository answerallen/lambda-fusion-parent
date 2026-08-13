package com.lambda.fusion.ai.chat.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.core.pagination.PageQuery;
import com.lambda.fusion.core.utils.AuthUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "对话会话分页查询参数")
public class ChatSessionPage extends PageQuery<ChatSessionEntity> {

    @Schema(description = "应用ID")
    private String appId;

    @Override
    public LambdaQueryWrapper<ChatSessionEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<ChatSessionEntity> wrapper = new LambdaQueryWrapper<>();
        String userId = AuthUtils.getUser().getUsername();
        wrapper.eq(StringUtils.isNotBlank(userId), ChatSessionEntity::getUserId, userId);
        wrapper.eq(StringUtils.isNotBlank(appId), ChatSessionEntity::getAppId, appId);
        wrapper.orderByDesc(ChatSessionEntity::getLastMessageAt);
        return wrapper;
    }
}
