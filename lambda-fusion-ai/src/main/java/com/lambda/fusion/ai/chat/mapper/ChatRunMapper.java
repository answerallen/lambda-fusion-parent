package com.lambda.fusion.ai.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChatRunMapper extends BaseMapper<ChatRunEntity> {

    /**
     * 取数据库当前时间，用于心跳写入与失效判定，避免节点时钟偏差。
     *
     * @return 数据库当前时间
     */
    @Select("SELECT CURRENT_TIMESTAMP")
    LocalDateTime selectDbNow();
}
