package com.lambda.fusion.ai.channel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.lambda.fusion.ai.channel.model.ChannelBindingDto;
import com.lambda.fusion.ai.channel.model.entity.ChannelConfigEntity;
import io.agentscope.harness.agent.gateway.channel.ChannelBinding;
import io.agentscope.harness.agent.gateway.channel.ChannelConfig;
import io.agentscope.harness.agent.gateway.channel.DmScope;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 验证 {@link ChannelConfigServiceImpl#buildConfig} 把 entity+绑定 DTO 映射为 harness
 * {@link ChannelConfig}/{@link ChannelBinding}，以及 {@link ChannelConfigServiceImpl#parseDmScope} 的回退。
 *
 * @author Jin
 */
class ChannelConfigConverterTest {

    @Test
    void buildConfigMapsAllFields() {
        ChannelBindingDto binding = new ChannelBindingDto();
        binding.setAgentId("app:1:t:2");
        binding.setPeer("peerA");
        binding.setParentPeer("parentA");
        binding.setGuild("guildA");
        binding.setRoles(Set.of("role1", "role2"));
        binding.setTeam("teamA");
        binding.setAccount("acctA");
        binding.setChannel("chanA");
        binding.setSessionScope("PER_PEER");

        ChannelConfigEntity entity = new ChannelConfigEntity();
        entity.setChannelId("dingtalk");
        entity.setDefaultAgentId("app:1:t:2");
        entity.setDmScope("PER_CHANNEL_PEER");
        entity.setBindings(List.of(binding));

        ChannelConfig config = ChannelConfigServiceImpl.buildConfig(entity);

        assertThat(config.channelId()).isEqualTo("dingtalk");
        assertThat(config.defaultAgentId()).isEqualTo("app:1:t:2");
        assertThat(config.dmScope()).isEqualTo(DmScope.PER_CHANNEL_PEER);
        assertThat(config.bindings()).hasSize(1);
        ChannelBinding b = config.bindings().get(0);
        assertThat(b.agentId()).isEqualTo("app:1:t:2");
        assertThat(b.peer()).isEqualTo("peerA");
        assertThat(b.parentPeer()).isEqualTo("parentA");
        assertThat(b.guild()).isEqualTo("guildA");
        assertThat(b.roles()).containsExactlyInAnyOrder("role1", "role2");
        assertThat(b.team()).isEqualTo("teamA");
        assertThat(b.account()).isEqualTo("acctA");
        assertThat(b.channel()).isEqualTo("chanA");
        assertThat(b.sessionScope()).isEqualTo(DmScope.PER_PEER);
    }

    @Test
    void buildConfigNullBindingsYieldsEmptyList() {
        ChannelConfigEntity entity = new ChannelConfigEntity();
        entity.setChannelId("c");
        entity.setDmScope("MAIN");
        entity.setBindings(null);

        ChannelConfig config = ChannelConfigServiceImpl.buildConfig(entity);

        assertThat(config.bindings()).isEmpty();
        assertThat(config.dmScope()).isEqualTo(DmScope.MAIN);
    }

    @Test
    void parseDmScopeValid() {
        assertThat(ChannelConfigServiceImpl.parseDmScope("PER_PEER")).isEqualTo(DmScope.PER_PEER);
    }

    @Test
    void parseDmScopeBlankOrInvalidFallsBackToDefault() {
        assertThat(ChannelConfigServiceImpl.parseDmScope(null)).isEqualTo(DmScope.defaultScope());
        assertThat(ChannelConfigServiceImpl.parseDmScope("")).isEqualTo(DmScope.defaultScope());
        assertThat(ChannelConfigServiceImpl.parseDmScope("NOT_A_SCOPE")).isEqualTo(DmScope.defaultScope());
    }
}
