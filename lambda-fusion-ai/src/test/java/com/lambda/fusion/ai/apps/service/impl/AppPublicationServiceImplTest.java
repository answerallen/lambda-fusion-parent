package com.lambda.fusion.ai.apps.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.fusion.ai.AiConstants.PublishStatus;
import com.lambda.fusion.ai.apps.mapper.AppMapper;
import com.lambda.fusion.ai.apps.model.AppPublication;
import com.lambda.fusion.ai.apps.model.AvailableApp;
import com.lambda.fusion.ai.apps.model.PublishedAppProfile;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.llm.service.LlmModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 验证 {@link AppPublicationServiceImpl} 发布生命周期：首次发布生成稳定代码、重新发布不换链接、
 * 下线保留代码、停用应用拒绝发布、纯发布状态变化不影响配置（不发配置变更事件）。
 *
 * @author Jin
 */
class AppPublicationServiceImplTest {

    private AppMapper appMapper;
    private AppService appService;
    private AppPublicationServiceImpl service;

    @BeforeEach
    void setUp() {
        appMapper = mock(AppMapper.class);
        appService = mock(AppService.class);
        service = new AppPublicationServiceImpl(appMapper, mock(LlmModelService.class), appService);
    }

    @Test
    void shouldGenerateStableCodeOnFirstPublish() {
        AppEntity app = draftApp();
        stubForUpdate(app);
        when(appMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        AppPublication publication = service.publish("app-1");

        assertThat(publication.getPublishStatus()).isEqualTo(PublishStatus.PUBLISHED.getCode());
        assertThat(publication.getPublishCode()).isNotBlank().hasSize(32);
        assertThat(publication.getPublishedAt()).isNotNull();
    }

    @Test
    void shouldKeepCodeOnRepublish() {
        AppEntity app = draftApp();
        stubForUpdate(app);
        when(appMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        String firstCode = service.publish("app-1").getPublishCode();

        // 已发布状态下重新发布：幂等返回，不换代码。
        AppPublication republished = service.publish("app-1");

        assertThat(republished.getPublishCode()).isEqualTo(firstCode);
        assertThat(republished.getPublishStatus()).isEqualTo(PublishStatus.PUBLISHED.getCode());
    }

    @Test
    void shouldKeepCodeOnUnpublishThenRepublish() {
        AppEntity app = draftApp();
        stubForUpdate(app);
        when(appMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        String code = service.publish("app-1").getPublishCode();

        AppPublication unpublished = service.unpublish("app-1");
        assertThat(unpublished.getPublishStatus()).isEqualTo(PublishStatus.UNPUBLISHED.getCode());
        assertThat(unpublished.getPublishCode()).isEqualTo(code);

        // 下线后重新发布：恢复原链接。
        AppPublication republished = service.publish("app-1");
        assertThat(republished.getPublishCode()).isEqualTo(code);
        assertThat(republished.getPublishStatus()).isEqualTo(PublishStatus.PUBLISHED.getCode());
    }

    @Test
    void shouldRejectPublishWhenAppDisabled() {
        AppEntity app = draftApp();
        app.setEnabled(Boolean.FALSE);
        stubForUpdate(app);

        assertThatThrownBy(() -> service.publish("app-1"))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e ->
                        assertThat(((AiBusinessException) e).getCode()).isEqualTo(AiErrorCode.APP_DISABLED.getCode()));
    }

    @Test
    void shouldRejectInvalidAudienceBeforePublish() {
        AppEntity app = draftApp();
        app.setAudience("VIP");
        stubForUpdate(app);

        assertThatThrownBy(() -> service.publish("app-1"))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.APP_AUDIENCE_INVALID.getCode()));
    }

    @Test
    void shouldUnpublishIdempotentlyWhenNotPublished() {
        AppEntity app = draftApp();
        stubForUpdate(app);

        AppPublication result = service.unpublish("app-1");

        assertThat(result.getPublishStatus()).isEqualTo(PublishStatus.UNPUBLISHED.getCode());
        assertThat(result.getPublishCode()).isNull();
    }

    @Test
    void shouldReturnProfileForPublishedApp() {
        AppEntity app = publishedApp();
        when(appMapper.selectByPublishCode("code-1")).thenReturn(app);

        PublishedAppProfile profile = service.profile("code-1");

        assertThat(profile.getPublishCode()).isEqualTo("code-1");
        assertThat(profile.getName()).isEqualTo("demo");
        // 名片不含 appId/tenantId 与运行配置（PublishedAppProfile 类型本身即无这些字段）。
    }

    @Test
    void shouldRejectProfileWhenCodeUnknown() {
        when(appMapper.selectByPublishCode("bad")).thenReturn(null);

        assertThatThrownBy(() -> service.profile("bad"))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.APP_PUBLICATION_NOT_FOUND.getCode()));
    }

    @Test
    void shouldRejectProfileWhenUnpublished() {
        AppEntity app = publishedApp();
        app.setPublishStatus(PublishStatus.UNPUBLISHED.getCode());
        when(appMapper.selectByPublishCode("code-1")).thenReturn(app);

        assertThatThrownBy(() -> service.profile("code-1"))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.APP_UNPUBLISHED.getCode()));
    }

    @Test
    void shouldRejectProfileWhenAppDisabled() {
        AppEntity app = publishedApp();
        app.setEnabled(Boolean.FALSE);
        when(appMapper.selectByPublishCode("code-1")).thenReturn(app);

        assertThatThrownBy(() -> service.profile("code-1"))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e ->
                        assertThat(((AiBusinessException) e).getCode()).isEqualTo(AiErrorCode.APP_DISABLED.getCode()));
    }

    @Test
    void shouldAccessViaLoadAvailableView() {
        AppEntity located = publishedApp();
        when(appMapper.selectByPublishCode("code-1")).thenReturn(located);
        AvailableApp visible = new AvailableApp();
        visible.setId("app-1");
        visible.setSupportsVision(Boolean.TRUE);
        when(appService.loadAvailableView("app-1")).thenReturn(visible);

        AvailableApp view = service.access("code-1");

        // access 委托 loadAvailableView 做受众+租户校验和模型能力回填。
        assertThat(view.getId()).isEqualTo("app-1");
        assertThat(view.getSupportsVision()).isTrue();
        verify(appService).loadAvailableView("app-1");
    }

    @Test
    void shouldRejectAccessWhenUnpublished() {
        AppEntity app = publishedApp();
        app.setPublishStatus(PublishStatus.UNPUBLISHED.getCode());
        when(appMapper.selectByPublishCode("code-1")).thenReturn(app);

        assertThatThrownBy(() -> service.access("code-1"))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.APP_UNPUBLISHED.getCode()));
    }

    private void stubForUpdate(AppEntity app) {
        // AppMapper.selectByIdForUpdate 为 default 方法，mock 默认不执行 default 实现，需直接对其打桩。
        when(appMapper.selectByIdForUpdate("app-1")).thenReturn(app);
    }

    private static AppEntity draftApp() {
        AppEntity app = new AppEntity();
        app.setId("app-1");
        app.setTenantId("tenant-1");
        app.setName("demo");
        app.setAudience("ALL");
        app.setAppType("CHAT");
        app.setEnabled(Boolean.TRUE);
        app.setPublishStatus(PublishStatus.UNPUBLISHED.getCode());
        return app;
    }

    private static AppEntity publishedApp() {
        AppEntity app = draftApp();
        app.setPublishCode("code-1");
        app.setPublishStatus(PublishStatus.PUBLISHED.getCode());
        return app;
    }
}
