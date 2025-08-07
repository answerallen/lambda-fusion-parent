package com.lambda.fusion.authority.user.domain;

import com.lambda.fusion.authority.user.UserInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "用户扩展信息")
@EqualsAndHashCode(callSuper = true)
public class UserInfo extends UserInfoDTO {

    private Integer passwordModifyDays;
}
