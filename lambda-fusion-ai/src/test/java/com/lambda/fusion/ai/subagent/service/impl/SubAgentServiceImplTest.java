package com.lambda.fusion.ai.subagent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.llm.service.LlmModelService;
import com.lambda.fusion.ai.subagent.mapper.SubAgentMapper;
import com.lambda.fusion.ai.subagent.model.entity.SubAgentEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 验证 {@link SubAgentServiceImpl#listEnabledByIds}：过滤禁用/不存在的子代理，
 * 按入参 ids 顺序返回。
 *
 * @author Jin
 */
class SubAgentServiceImplTest {

    private final SubAgentMapper subAgentMapper = mock(SubAgentMapper.class);
    private final SubAgentServiceImpl service =
            new SubAgentServiceImpl(subAgentMapper, mock(LlmModelService.class), mock(ApplicationEventPublisher.class));

    private static SubAgentEntity entity(String id, boolean enabled) {
        SubAgentEntity entity = new SubAgentEntity();
        entity.setId(id);
        entity.setEnabled(enabled);
        return entity;
    }

    @Test
    void filtersDisabledAndMissingAndPreservesOrder() {
        SubAgentEntity first = entity("1", true);
        SubAgentEntity disabled = entity("2", false);
        SubAgentEntity third = entity("3", true);
        when(subAgentMapper.selectByIds(anyCollection())).thenReturn(List.of(first, disabled, third));

        List<SubAgentEntity> result = service.listEnabledByIds(List.of("3", "1", "2", "404"));

        assertThat(result).extracting(SubAgentEntity::getId).containsExactly("3", "1");
    }

    @Test
    void emptyIdsShortCircuits() {
        assertThat(service.listEnabledByIds(List.of())).isEmpty();
        assertThat(service.listEnabledByIds(null)).isEmpty();
        verifyNoInteractions(subAgentMapper);
    }
}
