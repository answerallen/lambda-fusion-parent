package com.fusion.startup;

import com.lambda.autoconfig.NettyAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = NettyAutoConfiguration.class)
public class FusionApplication {

    public static void main(String[] args) {
        SpringApplication.run(FusionApplication.class, args);
    }
}
