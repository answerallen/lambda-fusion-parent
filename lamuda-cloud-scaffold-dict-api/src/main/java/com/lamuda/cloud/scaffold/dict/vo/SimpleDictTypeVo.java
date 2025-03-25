package com.lamuda.cloud.scaffold.dict.vo;

import com.lamuda.cloud.scaffold.dict.entity.DictInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import lombok.Data;

import java.util.List;

/**
 * @author Jin
 */
@Data
@Schema(description = "数据字典简略信息")
public class SimpleDictTypeVo {

    @Schema(description = "id", hidden = true)
    private String id;
    @Max(30)
    @Schema(required = true, description = "字典类型")
    private String dictType;
    @Schema(required = true, description = "字典描述", example = "民族")
    private String dictName;
    @Schema(description = " 数据类型 0/null 静态类型 1 url  2 sql 3 enum")
    private Integer dataType;
    @Schema(description = " 类型参数, 最大长度512")
    private String dataTypeValue;
    @Schema(description = "子节点数据", hidden = true)
    private List<DictInfo> data;


}
