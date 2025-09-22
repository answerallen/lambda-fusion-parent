package com.lambda.fusion.dict.model.dto;

import com.lambda.cloud.core.base.BaseDTO;
import com.lambda.cloud.core.convert.BaseConverter;
import com.lambda.fusion.dict.model.vo.DictInfoVO;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字典信息查询参数")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class DictInfoQueryDTO extends BaseDTO<DictInfoQueryDTO, DictInfoVO> {

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

    @Override
    protected BaseConverter<DictInfoQueryDTO, DictInfoVO> getConverter() {
        return null;
    }
}
