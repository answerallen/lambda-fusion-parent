package com.lambda.fusion.authority.authentication.model;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyUser implements Serializable {
    private String thirdType;
    private String openId;
    private String username;
    private String nickname;
    private String avatar;
    private String remark;
}
