package com.lambda.fusion.ai.apps.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppMapper extends BaseMapper<AppEntity> {

    /**
     * 按主键查询并加行锁（FOR UPDATE），供发布等需要串行化同一应用行的流程使用。
     *
     * @param id 应用ID
     * @return 应用实体；不存在返回 null
     */
    default AppEntity selectByIdForUpdate(String id) {
        return selectOne(
                new LambdaQueryWrapper<AppEntity>().eq(AppEntity::getId, id).last("FOR UPDATE"));
    }
}
