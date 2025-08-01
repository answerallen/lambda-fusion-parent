package com.lambda.fusion.dict.vo;

import com.lambda.fusion.dict.dao.entity.DictInfo;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import java.util.List;
import lombok.Data;

/**
 * @author Jin
 */
@SuppressFBWarnings({"EI_EXPOSE_REP"})
@Data
@Schema(description = "数据字典简略信息")
public class DictTypeVo {

    @Schema(description = "id", hidden = true)
    private String id;

    @Max(30)
    @Schema(description = "字典类型")
    private String dictType;

    @Schema(description = "字典描述", example = "民族")
    private String dictName;

    @Schema(description = " 数据类型 0/null 静态类型 1 url  2 sql 3 enum")
    private Integer dataType;

    @Schema(description = " 类型参数, 最大长度512")
    private String dataTypeValue;

    @Schema(description = "子节点数据", hidden = true)
    private List<DictInfo> data;
}
