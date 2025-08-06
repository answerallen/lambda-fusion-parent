package com.lambda.fusion.auth.resource.bean;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.BooleanUtils;

/**
 * 按钮信息
 */
@Setter
@Getter
@Schema(description = "按钮信息")
@EqualsAndHashCode

public class Button {

    /**
     * 类型默认为3
     */
    private Integer type = 3;
    @Schema(required = true, description =  "按钮编号")
    private String id;
    @Schema(required = true, description =  "按钮名称")
    private String name;
    @Schema(description = "按钮key")
    private String key;
    @Schema(description = "所有页面")
    private String parentId;
    @Schema(description = "按钮方法函数")
    private String method;
    @Schema(description = "是否隐藏")
    private boolean hidden = false;
    @Schema(required = true, description =  "按钮级别")
    private int rank;
    @Schema(required = true, description =  "按钮顺序")
    private int orderNo;

    public Button() {
    }

    public Button(String id, String name, String key, String parentId, int rank, int orderNo) {
        this.id = id;
        this.name = name;
        this.key = key;
        this.parentId = parentId;
        this.rank = rank;
        this.orderNo = orderNo;
    }

    public Button(Resource resource) {
        this.id = resource.getId();
        this.name = resource.getResName();
        this.key = resource.getResPath();
        this.parentId = resource.getParentId();
        this.rank = resource.getResRank();
        this.orderNo = resource.getOrderNo();
        this.hidden = BooleanUtils.toBoolean(resource.isHidden());
    }
}
