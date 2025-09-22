package com.lambda.fusion.dict.converter;

import com.lambda.cloud.core.convert.AutoConverter;
import com.lambda.cloud.core.convert.BaseConverter;
import com.lambda.fusion.dict.model.dto.DictInfoInputDTO;
import com.lambda.fusion.dict.model.entity.DictInfoEntity;
import org.mapstruct.Mapping;

@AutoConverter
public interface DictInfoDtoConverter extends BaseConverter<DictInfoInputDTO, DictInfoEntity> {

    @Mapping(source = "additional", target = "extra", qualifiedByName = "mapToString")
    @Override
    DictInfoEntity convertTo(DictInfoInputDTO source);
}
