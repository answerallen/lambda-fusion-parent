package com.lambda.fusion.dict.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.Map;

@AutoConverter(target = DictInfo.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字典信息查询参数")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class QueryDictInfo {

    @Schema(description = "字典类型")
    private String dictType;

    @Schema(description = "字段类型")
    private String fieldType;

    @Schema(description = "字段名称")
    private String fieldName;

    @Schema(description = "父级ID")
    private String parentId;

    @Schema(description = "启用状态")
    private Integer enableState;

    @Schema(description = "可选择状态")
    private Integer selectable;

    @Schema(description = "字典信息ID")
    private String dictInfoId;

    @Schema(description = "扩展参数")
    private Map<String, Object> extraParams;

}
