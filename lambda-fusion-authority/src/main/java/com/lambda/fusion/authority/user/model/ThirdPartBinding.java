package com.lambda.fusion.authority.user.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartBinding {

    private String loginType;
    private String loginTypeLabel;
    private String openId;
    private String username;
}
