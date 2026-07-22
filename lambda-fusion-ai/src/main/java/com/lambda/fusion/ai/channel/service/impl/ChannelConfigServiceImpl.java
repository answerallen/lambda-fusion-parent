package com.lambda.fusion.ai.channel.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.channel.mapper.ChannelConfigMapper;
import com.lambda.fusion.ai.channel.model.ChannelBindingDto;
import com.lambda.fusion.ai.channel.model.ChannelConfigPage;
import com.lambda.fusion.ai.channel.model.ChannelDefinition;
import com.lambda.fusion.ai.channel.model.CreateChannelConfig;
import com.lambda.fusion.ai.channel.model.UpdateChannelConfig;
import com.lambda.fusion.ai.channel.model.entity.ChannelConfigEntity;
import com.lambda.fusion.ai.channel.service.ChannelConfigService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.runtime.gateway.ChannelBootstrap;
import com.lambda.fusion.ai.security.KeyEncryptionService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.harness.agent.gateway.channel.ChannelBinding;
import io.agentscope.harness.agent.gateway.channel.ChannelConfig;
import io.agentscope.harness.agent.gateway.channel.DmScope;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 通道路由配置服务。
 *
 * <p>平台凭证（{@code properties}）以 AES 密文落库（{@code properties_encrypted}），读取时解密。
 * CRUD 后通过 {@link ChannelBootstrap#rebuild} 重建运行中的 channel（best-effort）。
 * 不发 {@code ConfigChangedEvent}：路由/渠道配置不影响 agent 缓存。
 *
 * @author Jin
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ChannelConfigServiceImpl implements ChannelConfigService {

    private final ChannelConfigMapper channelConfigMapper;
    private final KeyEncryptionService keyEncryptionService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ChannelBootstrap> bootstrapProvider;

    @Override
    public Page<ChannelConfigEntity> page(ChannelConfigPage query) {
        return channelConfigMapper.selectPage(query.getPage(), query.getLambdaQueryWrapper());
    }

    @Override
    public ChannelConfigEntity get(String id) {
        return requireExists(id);
    }

    @Override
    public ChannelConfigEntity getByChannelId(String channelId) {
        return channelConfigMapper.selectOne(
                new LambdaQueryWrapper<ChannelConfigEntity>().eq(ChannelConfigEntity::getChannelId, channelId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelConfigEntity create(CreateChannelConfig dto) {
        ensureChannelIdUnique(dto.getChannelId());
        ChannelConfigEntity entity = getChannelConfigEntity(dto);
        channelConfigMapper.insert(entity);
        rebuild(entity.getChannelId());
        return entity;
    }

    private @NonNull ChannelConfigEntity getChannelConfigEntity(CreateChannelConfig dto) {
        ChannelConfigEntity entity = new ChannelConfigEntity();
        entity.setId(IdUtil.getSnowflakeNextIdStr());
        entity.setChannelId(dto.getChannelId());
        entity.setType(dto.getType());
        entity.setDefaultAgentId(dto.getDefaultAgentId());
        entity.setDmScope(StringUtils.defaultIfBlank(
                dto.getDmScope(), DmScope.defaultScope().name()));
        entity.setBindings(dto.getBindings());
        entity.setPropertiesEncrypted(encryptProperties(dto.getProperties()));
        entity.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));
        entity.setRemark(dto.getRemark());
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, UpdateChannelConfig dto) {
        ChannelConfigEntity entity = requireExists(id);
        if (StringUtils.isNotBlank(dto.getType())) {
            entity.setType(dto.getType());
        }
        if (dto.getDefaultAgentId() != null) {
            entity.setDefaultAgentId(dto.getDefaultAgentId());
        }
        if (StringUtils.isNotBlank(dto.getDmScope())) {
            entity.setDmScope(dto.getDmScope());
        }
        if (dto.getBindings() != null) {
            entity.setBindings(dto.getBindings());
        }
        if (dto.getProperties() != null) {
            entity.setPropertiesEncrypted(encryptProperties(dto.getProperties()));
        }
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
        if (dto.getRemark() != null) {
            entity.setRemark(dto.getRemark());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        channelConfigMapper.updateById(entity);
        rebuild(entity.getChannelId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        ChannelConfigEntity entity = requireExists(id);
        channelConfigMapper.deleteById(id);
        rebuild(entity.getChannelId());
    }

    @Override
    public ChannelConfigEntity loadById(String id) {
        return requireExists(id);
    }

    @Override
    public ChannelConfig resolve(String channelId) {
        ChannelConfigEntity entity = channelConfigMapper.selectOne(new LambdaQueryWrapper<ChannelConfigEntity>()
                .eq(ChannelConfigEntity::getChannelId, channelId)
                .eq(ChannelConfigEntity::getEnabled, Boolean.TRUE));
        if (entity == null) {
            return null;
        }
        return buildConfig(entity);
    }

    @Override
    public List<ChannelDefinition> loadEnabledDefinitions() {
        return channelConfigMapper
                .selectList(
                        new LambdaQueryWrapper<ChannelConfigEntity>().eq(ChannelConfigEntity::getEnabled, Boolean.TRUE))
                .stream()
                .map(this::toDefinition)
                .toList();
    }

    @Override
    public ChannelDefinition resolveDefinition(String channelId) {
        ChannelConfigEntity entity = channelConfigMapper.selectOne(new LambdaQueryWrapper<ChannelConfigEntity>()
                .eq(ChannelConfigEntity::getChannelId, channelId)
                .eq(ChannelConfigEntity::getEnabled, Boolean.TRUE));
        if (entity == null) {
            return null;
        }
        return toDefinition(entity);
    }

    static ChannelConfig buildConfig(ChannelConfigEntity entity) {
        List<ChannelBinding> bindings = entity.getBindings() == null
                ? List.of()
                : entity.getBindings().stream()
                        .map(ChannelConfigServiceImpl::toBinding)
                        .toList();
        return new ChannelConfig(
                entity.getChannelId(), entity.getDefaultAgentId(), parseDmScope(entity.getDmScope()), bindings);
    }

    private static ChannelBinding toBinding(ChannelBindingDto dto) {
        Set<String> roles = dto.getRoles() == null ? Set.of() : dto.getRoles();
        return new ChannelBinding(
                dto.getAgentId(),
                dto.getPeer(),
                dto.getParentPeer(),
                dto.getGuild(),
                roles,
                dto.getTeam(),
                dto.getAccount(),
                dto.getChannel(),
                parseDmScope(dto.getSessionScope()));
    }

    // 空值或非法 DmScope 使用默认会话粒度。
    static DmScope parseDmScope(String value) {
        if (StringUtils.isBlank(value)) {
            return DmScope.defaultScope();
        }
        try {
            return DmScope.valueOf(value);
        } catch (IllegalArgumentException e) {
            return DmScope.defaultScope();
        }
    }

    private ChannelDefinition toDefinition(ChannelConfigEntity entity) {
        return new ChannelDefinition(
                entity.getChannelId(),
                entity.getType(),
                decryptProperties(entity.getPropertiesEncrypted()),
                buildConfig(entity));
    }

    // 空凭证不落库；非空凭证序列化为 JSON 后加密。
    private String encryptProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return null;
        }
        try {
            return keyEncryptionService.encrypt(objectMapper.writeValueAsString(properties));
        } catch (Exception e) {
            throw new AiBusinessException(AiErrorCode.CONFIGURATION_ERROR, "渠道凭证加密失败: " + e.getMessage());
        }
    }

    // 未配置凭证时返回空 Map；否则解密后解析 JSON。
    private Map<String, Object> decryptProperties(String encrypted) {
        if (StringUtils.isBlank(encrypted)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(keyEncryptionService.decrypt(encrypted), new TypeReference<>() {});
        } catch (Exception e) {
            throw new AiBusinessException(AiErrorCode.CONFIGURATION_ERROR, "渠道凭证解密失败: " + e.getMessage());
        }
    }

    // 尽量重建运行中的 channel；Gateway 未启用或重建失败不影响 CRUD。
    private void rebuild(String channelId) {
        ChannelBootstrap bootstrap = bootstrapProvider.getIfAvailable();
        if (bootstrap == null) {
            return;
        }
        try {
            bootstrap.rebuild(channelId);
        } catch (Exception e) {
            log.warn("重建渠道 {} 失败: {}", channelId, e.getMessage());
        }
    }

    private ChannelConfigEntity requireExists(String id) {
        ChannelConfigEntity entity = channelConfigMapper.selectOne(
                new LambdaQueryWrapper<ChannelConfigEntity>().eq(ChannelConfigEntity::getId, id));
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.CHANNEL_CONFIG_NOT_FOUND, id);
        }
        return entity;
    }

    private void ensureChannelIdUnique(String channelId) {
        boolean exists = channelConfigMapper.exists(
                new LambdaQueryWrapper<ChannelConfigEntity>().eq(ChannelConfigEntity::getChannelId, channelId));
        if (exists) {
            throw new AiBusinessException(AiErrorCode.CHANNEL_CONFIG_CHANNEL_ID_EXISTS, channelId);
        }
    }
}
