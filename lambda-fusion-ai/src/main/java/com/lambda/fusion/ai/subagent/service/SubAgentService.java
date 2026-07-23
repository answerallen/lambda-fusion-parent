package com.lambda.fusion.ai.subagent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.subagent.model.CreateSubAgent;
import com.lambda.fusion.ai.subagent.model.SubAgentPage;
import com.lambda.fusion.ai.subagent.model.UpdateSubAgent;
import com.lambda.fusion.ai.subagent.model.entity.SubAgentEntity;
import java.util.List;

public interface SubAgentService {

    Page<SubAgentEntity> page(SubAgentPage query);

    SubAgentEntity get(String id);

    SubAgentEntity create(CreateSubAgent dto);

    void update(String id, UpdateSubAgent dto);

    void delete(String id);

    /**
     * 按主键加载（含禁用记录）；不存在抛出业务异常。
     */
    SubAgentEntity loadById(String id);

    /**
     * 按 id 列表加载启用中的子代理（过滤禁用/不存在），按入参 ids 顺序返回。
     * 供 {@code AgentFactory} 构建期解析子代理声明。
     */
    List<SubAgentEntity> listEnabledByIds(List<String> ids);
}
