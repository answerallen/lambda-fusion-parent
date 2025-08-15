package com.lambda.fusion.core.events;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Jin
 */
@Getter
@Setter
@SuppressFBWarnings({"EI_EXPOSE_REP"})
public class LambdaWsConnectEvent {

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
