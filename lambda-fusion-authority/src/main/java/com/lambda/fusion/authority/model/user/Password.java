package com.lambda.fusion.authority.model.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Password {
    String origin;
    String encrypted;
}
