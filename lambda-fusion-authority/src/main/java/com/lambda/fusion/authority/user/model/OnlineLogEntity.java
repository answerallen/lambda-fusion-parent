package com.lambda.fusion.authority.user.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("la_user_online_logs")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class OnlineLogEntity {

    @TableField("USERNAME")
    private String username;

    @TableField("IP")
    private String ip;

    @TableField("TYPE")
    private int type;

    @TableField("IS_ONLINE")
    private Integer isOnline;

    @TableField("ONLINE_TIME")
    private LocalDateTime onlineTime;

    @TableField("OFFLINE_TIME")
    private LocalDateTime offlineTime;
}
