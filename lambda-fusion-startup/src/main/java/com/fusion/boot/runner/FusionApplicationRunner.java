package com.fusion.boot.runner;

import com.lambda.autoconfig.SmsProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@SuppressFBWarnings("EI_EXPOSE_REP2")
@Slf4j
@Component
public class FusionApplicationRunner implements ApplicationRunner {

    private SmsProperties smsProperties;

    @Autowired
    public void setSmsProperties(SmsProperties smsProperties) {
        this.smsProperties = smsProperties;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        log.info("smsProperties:  aliyun.enabled={}", smsProperties.getAliyun().isEnabled());
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(
                () -> {
                    log.info(
                            "检测配置刷新 smsProperties:  aliyun.enabled={}",
                            smsProperties.getAliyun().isEnabled());
                },
                0,
                10,
                TimeUnit.SECONDS);
    }
}
