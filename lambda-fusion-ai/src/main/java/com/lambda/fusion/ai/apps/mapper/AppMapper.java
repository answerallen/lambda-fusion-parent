package com.lambda.fusion.ai.apps.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    /**
     * 按全局唯一 publish_code 精确命中一行（发布入口公开资料查询的受控跨租户例外）。
     *
     * <p>匿名 profile 无租户上下文，需绕过租户插件按高熵唯一代码精确查询；仅限本方法，
     * 禁止用于列表、模糊查询或任何登录后路径（登录后应在租户上下文内按普通查询进行）。
     * 用显式 @Select 让 MyBatis 代理识别 {@code @InterceptorIgnore}（default 方法不被代理）。
     *
     * @param publishCode 发布代码
     * @return 应用实体；不存在返回 null
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM ai_app WHERE publish_code = #{publishCode} LIMIT 1")
    AppEntity selectByPublishCode(@Param("publishCode") String publishCode);
}
