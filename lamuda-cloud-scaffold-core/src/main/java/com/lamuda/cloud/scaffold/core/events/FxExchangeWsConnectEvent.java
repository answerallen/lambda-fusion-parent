package com.lamuda.cloud.scaffold.core.events;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * @author Jin
 */
@Getter
@Setter
public class FxExchangeWsConnectEvent {

    /**
     * 用户名
     */
    private String username;

    /**
     * 是否在线
     */
    private boolean online;

    /**
     * 客户端ip
     */
    private String ip;

    /**
     * 上线时间
     */
    private Date onlineTime;

    /**
     * 离线时间
     */
    private Date offlineTime;

}
