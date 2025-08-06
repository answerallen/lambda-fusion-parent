package com.lambda.fusion.auth.user.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;


@Data
@TableName("m1_online_log")

public class OnlineLog {

    @TableId("USERNAME")
    private String username;

    @TableField("IP")
    private String ip;

    @TableField("TYPE")
    private int type;

    @TableField("ON_LINE")
    private boolean online;

    @TableField("ONLINE_TIME")
    private Date onlineTime;

    @TableField("OFFLINE_TIME")
    private Date offlineTime;
}
