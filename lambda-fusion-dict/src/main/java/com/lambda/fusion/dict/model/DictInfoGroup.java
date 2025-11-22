package com.lambda.fusion.dict.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 数据字典分组信息
 * @author Jin
 */
@SuppressFBWarnings("EI_EXPOSE_REP")
@Getter
@Setter
@Schema(description = "数据字典分组信息")
public class DictInfoGroup {
    /**
     * 字典类型
     */
    private String dictType;

    /**
     *字典信息
     */
    private List<DictionaryEntry> dictList;
}
