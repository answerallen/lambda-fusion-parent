package com.lambda.fusion.authority.resource.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lambda.cloud.core.annotation.AutoConverter;
import lombok.Getter;
import lombok.Setter;

@AutoConverter(target = Resource.class)
@Getter
@Setter
@TableName("LA_RESOURCES")
public class ResourceEntity {

    @TableId
    private String id;

    private String resName;

    private String resPath;

    private String resUrl;

    private String parentId;

    private Integer resLevel;

    private Integer orderNo;

    private String icon;

    private String method;

    private Boolean hidden = false;

    private Integer resType;

    private String parentKeys;

    private Integer resMode;

    private String remark;

    private String keyName;

    private Boolean keepAlive;

    private String expand;

    private String businessExpand;
}
