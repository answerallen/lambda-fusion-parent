package com.lambda.fusion.ai.apps.service.impl;

import cn.hutool.core.util.IdUtil;
import com.lambda.fusion.ai.AiConstants.AppAudience;
import com.lambda.fusion.ai.AiConstants.PublishStatus;
import com.lambda.fusion.ai.apps.mapper.AppMapper;
import com.lambda.fusion.ai.apps.model.AppPublication;
import com.lambda.fusion.ai.apps.model.AvailableApp;
import com.lambda.fusion.ai.apps.model.PublishedAppProfile;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.apps.service.AppPublicationService;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.llm.service.LlmModelService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用发布服务实现。
 *
 * <p>发布事务：行锁串行化同一应用行 → 校验存在/启用/模型有效/受众合法 → 首次生成 publishCode
 * （唯一索引冲突时重新生成有限次）→ 置 PUBLISHED 与 publishedAt。纯发布状态变化不影响 Agent
 * 配置，不发 {@code ConfigChangedEvent}（见发布设计 §4）。下线仅关闭独立 URL，保留代码。
 *
 * @author Jin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppPublicationServiceImpl implements AppPublicationService {

    /** publishCode 全局唯一冲突时的最大重试次数（32 位随机 UUID 碰撞概率极低，有限次兜底）。 */
    private static final int MAX_CODE_RETRIES = 3;

    private final AppMapper appMapper;
    private final LlmModelService llmModelService;
    /**
     * 受众与租户一致性校验复用 {@link AppService#loadAvailable} 单一事实来源。
     * {@code @Lazy} 避免与 AppService 的潜在构造环（本类不被 AppService 依赖，仅为防御）。
     */
    @Lazy
    private final AppService appService;

    @Override
    public AppPublication get(String appId) {
        return toPublication(requireExists(appId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppPublication publish(String appId) {
        AppEntity app = requireExistsForUpdate(appId);
        if (PublishStatus.PUBLISHED.getCode().equals(app.getPublishStatus())) {
            // 幂等：已发布直接返回现状，不改变代码与时间。
            return toPublication(app);
        }
        if (!Boolean.TRUE.equals(app.getEnabled())) {
            throw new AiBusinessException(AiErrorCode.APP_DISABLED, appId);
        }
        // 发布前校验模型仍有效、受众合法（与创建/更新同一权威口径）。
        llmModelService.loadById(app.getModelId());
        if (AppAudience.of(StringUtils.defaultIfBlank(app.getAudience(), AppAudience.ALL.getCode())) == null) {
            throw new AiBusinessException(AiErrorCode.APP_AUDIENCE_INVALID, app.getAudience());
        }
        if (StringUtils.isBlank(app.getPublishCode())) {
            app.setPublishCode(generateUniqueCode());
        }
        app.setPublishStatus(PublishStatus.PUBLISHED.getCode());
        app.setPublishedAt(LocalDateTime.now());
        app.setUpdatedAt(LocalDateTime.now());
        appMapper.updateById(app);
        return toPublication(app);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppPublication unpublish(String appId) {
        AppEntity app = requireExistsForUpdate(appId);
        if (!PublishStatus.PUBLISHED.getCode().equals(app.getPublishStatus())) {
            // 幂等：未发布直接返回现状。
            return toPublication(app);
        }
        // 下线只关闭独立 URL，保留 publishCode，重新发布不换链接。
        app.setPublishStatus(PublishStatus.UNPUBLISHED.getCode());
        app.setUpdatedAt(LocalDateTime.now());
        appMapper.updateById(app);
        return toPublication(app);
    }

    /** 生成全局唯一的 publishCode；唯一索引冲突时重新生成有限次，仍冲突则报错（几乎不可能）。 */
    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_RETRIES; attempt++) {
            String code = IdUtil.fastSimpleUUID();
            try {
                // 以一次只读探测代替先写后撞：唯一索引仍是最终防线。
                if (appMapper.selectCount(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AppEntity>()
                                        .eq(AppEntity::getPublishCode, code))
                        == 0) {
                    return code;
                }
            } catch (DuplicateKeyException conflict) {
                log.warn("publishCode 唯一冲突，重新生成: attempt={}", attempt);
            }
        }
        throw new AiBusinessException(AiErrorCode.APP_PUBLISH_CODE_CONFLICT);
    }

    private AppEntity requireExists(String appId) {
        AppEntity entity = appMapper.selectById(appId);
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.APP_NOT_FOUND, appId);
        }
        return entity;
    }

    private AppEntity requireExistsForUpdate(String appId) {
        AppEntity entity = appMapper.selectByIdForUpdate(appId);
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.APP_NOT_FOUND, appId);
        }
        return entity;
    }

    @Override
    public PublishedAppProfile profile(String publishCode) {
        AppEntity app = requireByPublishCode(publishCode);
        requirePublished(app);
        requireEnabled(app);
        PublishedAppProfile profile = new PublishedAppProfile();
        profile.setPublishCode(app.getPublishCode());
        profile.setName(app.getName());
        profile.setAvatar(app.getAvatar());
        profile.setDescription(app.getDescription());
        return profile;
    }

    @Override
    public AvailableApp access(String publishCode) {
        // 先按发布代码定位应用（跨租户精确查询仅为拿到 appId）；再以 loadAvailable 在登录租户
        // 上下文内做启用+受众+租户一致性校验——别租户应用因租户插件查不到而拒绝，受众不符抛
        // APP_NOT_FOUND（前端据此显示无权限，不当作未登录）。
        AppEntity located = requireByPublishCode(publishCode);
        requirePublished(located);
        AppEntity app = appService.loadAvailable(located.getId());
        return AppService.toAvailableView(app);
    }

    private AppEntity requireByPublishCode(String publishCode) {
        if (StringUtils.isBlank(publishCode)) {
            throw new AiBusinessException(AiErrorCode.APP_PUBLICATION_NOT_FOUND, publishCode);
        }
        AppEntity app = appMapper.selectByPublishCode(publishCode);
        if (app == null) {
            throw new AiBusinessException(AiErrorCode.APP_PUBLICATION_NOT_FOUND, publishCode);
        }
        return app;
    }

    private void requirePublished(AppEntity app) {
        if (!PublishStatus.PUBLISHED.getCode().equals(app.getPublishStatus())) {
            throw new AiBusinessException(AiErrorCode.APP_UNPUBLISHED, app.getId());
        }
    }

    private void requireEnabled(AppEntity app) {
        if (!Boolean.TRUE.equals(app.getEnabled())) {
            throw new AiBusinessException(AiErrorCode.APP_DISABLED, app.getId());
        }
    }

    private static AppPublication toPublication(AppEntity app) {
        AppPublication publication = new AppPublication();
        publication.setAppId(app.getId());
        publication.setPublishCode(app.getPublishCode());
        publication.setPublishStatus(
                StringUtils.defaultIfBlank(app.getPublishStatus(), PublishStatus.UNPUBLISHED.getCode()));
        publication.setPublishedAt(app.getPublishedAt());
        return publication;
    }
}
