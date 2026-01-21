package com.lambda.fusion.authority.user.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Date;
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

    @TableField("ON_LINE")
    private Integer online;

    @TableField("ONLINE_TIME")
    private Date onlineTime;

    @TableField("OFFLINE_TIME")
    private Date offlineTime;
}
