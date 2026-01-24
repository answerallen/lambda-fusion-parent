package com.lambda.fusion.authority.user.model;

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