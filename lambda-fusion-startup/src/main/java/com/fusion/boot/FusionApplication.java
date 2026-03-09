package com.fusion.boot;

import com.lambda.autoconfig.SmsProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@SuppressFBWarnings("EI_EXPOSE_REP2")
@Component
@SpringBootApplication
public class FusionApplication {

    public static void main(String[] args) {
        SpringApplication.run(FusionApplication.class, args);
    }

    private SmsProperties smsProperties;
    private Environment environment;

    @Autowired
    public void setSmsProperties(SmsProperties smsProperties) {
        this.smsProperties = smsProperties;
    }

    @Autowired
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void test() {

        ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(1);
        scheduledExecutorService.scheduleAtFixedRate(
                () -> {
                    try {
                        System.out.println(smsProperties.getAliyun().isEnabled());
                        String property = environment.getProperty("lambda.sms.aliyun.enabled");
                        System.out.println(property);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                0,
                10,
                TimeUnit.SECONDS);
    }
}
