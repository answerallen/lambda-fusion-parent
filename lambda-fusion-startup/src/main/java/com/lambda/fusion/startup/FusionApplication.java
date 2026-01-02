package com.lambda.fusion.startup;

import com.lambda.fusion.core.annotation.LambdaFusionApplication;
import org.springframework.boot.SpringApplication;

@LambdaFusionApplication
public class FusionApplication {

    public static void main(String[] args) {
        SpringApplication.run(FusionApplication.class, args);
    }
}
