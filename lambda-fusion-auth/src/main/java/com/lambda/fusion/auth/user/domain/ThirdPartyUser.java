package com.lambda.fusion.auth.user.domain;


import lombok.Data;


@Data

public class ThirdPartyUser {

    private String openid;

    private String name;
}
