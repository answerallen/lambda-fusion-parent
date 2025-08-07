package com.lambda.fusion.authority.user.assembler;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserInfoAssembler {

    UserInfoAssembler INSTANCE = Mappers.getMapper(UserInfoAssembler.class);
}
