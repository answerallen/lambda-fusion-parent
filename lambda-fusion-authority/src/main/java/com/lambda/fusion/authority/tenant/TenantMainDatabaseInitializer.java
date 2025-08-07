package com.lambda.fusion.authority.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * 租户主数据库初始化
 */
@Slf4j
public class TenantMainDatabaseInitializer implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("租户主库liquibase脚本执行完毕。");
    }
}
