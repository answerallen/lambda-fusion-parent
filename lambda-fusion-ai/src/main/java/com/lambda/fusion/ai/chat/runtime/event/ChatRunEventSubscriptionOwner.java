package com.lambda.fusion.ai.chat.runtime.event;

import com.lambda.fusion.ai.chat.runtime.event.memory.QueuedEventSubscription;

/**
 * 事件订阅所属缓冲的注销回调：供 {@link QueuedEventSubscription} 在关闭或发送失败时从宿主注销，
 * 使订阅实现与具体缓冲存储解耦。
 *
 * @author Jin
 */
public interface ChatRunEventSubscriptionOwner {

    /**
     * 注销指定订阅。
     *
     * @param subscriptionId 订阅标识
     * @param identity 订阅实例
     */
    void detach(String subscriptionId, QueuedEventSubscription identity);
}
