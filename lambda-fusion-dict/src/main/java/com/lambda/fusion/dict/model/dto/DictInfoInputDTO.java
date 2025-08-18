package com.lambda.fusion.dict.model.dto;

import cn.hutool.extra.spring.SpringUtil;
import com.lambda.cloud.core.base.BaseDTO;
import com.lambda.cloud.core.convert.BaseConverter;
import com.lambda.fusion.dict.converter.DictInfoDtoConverter;
import com.lambda.fusion.dict.model.entity.DictInfoEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

import static com.lambda.fusion.dict.common.constants.DictConstants.*;

/**
 * @author Jin
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "更新数据字典对象")
public class DictInfoInputDTO extends BaseDTO<DictInfoInputDTO, DictInfoEntity> {
    @Override
    protected BaseConverter<DictInfoInputDTO, DictInfoEntity> getConverter() {
        return SpringUtil.getBean(DictInfoDtoConverter.class);
    }

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
