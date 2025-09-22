package com.lambda.fusion.dict.model.dto;

import static com.lambda.fusion.dict.common.constants.DictConstants.*;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import com.lambda.fusion.dict.converter.DictInfoConverter;
import com.lambda.fusion.dict.model.entity.DictInfoEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Jin
 */
@AutoConverter(converter = DictInfoConverter.class, target = DictInfoEntity.class)
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "更新数据字典对象")
public class DictInfoInputDTO extends BaseDTO<DictInfoEntity> {

    @NotBlank
    String id;

    @NotBlank(message = MSG_DICT_FIELD_NAME_NOT_EMPTY)
    @Max(200)
    String fieldName;

    @NotNull(message = MSG_DICT_SORT_NUMBER_NOT_EMPTY)
    Integer sort;

    @NotNull(message = MSG_DICT_ENABLED_NOT_EMPTY)
    Integer enableState;

    @NotNull
    Integer selectable;

    @NotBlank
    String fieldType;

    String remarks;

    @NotBlank
    @Max(30)
    private String dictType;

    private Additional additional;

    @Getter
    @Setter
    public static class Additional {
        private Map<String, Object> parameters;
    }
}
