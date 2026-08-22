package com.lambda.fusion.ai.chat.runtime.event;

/**
 * 对话执行事件订阅。
 *
 * @author Jin
 */
public interface ChatRunEventSubscription extends AutoCloseable {

    /** 关闭订阅。 */
    @Override
    void close();
}
