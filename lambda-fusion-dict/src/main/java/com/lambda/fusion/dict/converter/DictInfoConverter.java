package com.lambda.fusion.dict.converter;

import com.lambda.cloud.core.convert.BaseConverter;
import com.lambda.fusion.dict.model.dto.DictInfoInputDTO;
import com.lambda.fusion.dict.model.entity.DictInfoEntity;
import org.mapstruct.Mapping;

public interface DictInfoConverter extends BaseConverter<DictInfoInputDTO, DictInfoEntity> {

    @Mapping(source = "dictType", target = "dictType")
    DictInfoEntity convertTo(DictInfoInputDTO source);
}
