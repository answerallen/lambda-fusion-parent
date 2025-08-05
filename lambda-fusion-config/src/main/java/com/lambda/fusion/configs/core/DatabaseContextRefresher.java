package com.lambda.fusion.configs.core;

import com.zaxxer.hikari.HikariConfig;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

/**
 * 每隔30秒检测下数据库变动，可通过“lambda.fusion.config.auto-refresh.enabled=false”进行关闭
 *
 */
@Slf4j
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class DatabaseContextRefresher
        implements ApplicationRunner, EnvironmentAware, BeanFactoryAware, SmartApplicationListener {
    private final Lock lock = new ReentrantLock();
    protected static final ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1, run -> {
        Thread thread = new Thread(run);
        thread.setDaemon(true);
        thread.setName("DatabaseContextRefresher");
        return thread;
    });
    protected ConfigurableEnvironment environment;
    protected DefaultListableBeanFactory beanFactory;
    protected HikariConfig configuration;
    private final DatabaseBasedPropertySourceLocator databaseBasedPropertySourceLocator;

    public DatabaseContextRefresher(DatabaseBasedPropertySourceLocator databaseBasedPropertySourceLocator) {
        this.databaseBasedPropertySourceLocator = databaseBasedPropertySourceLocator;
    }

    public void apply() {
        if (databaseBasedPropertySourceLocator.changed(environment)) {
            log.debug("The config data has been changed! Ready to refresh..");
            doRefresh();
        }
    }

    public void doRefresh() {
        if (lock.tryLock()) {
            log.debug("Successfully acquired refresh lock");
            try {
                final ContextRefresher contextRefresher = beanFactory.getBean(ContextRefresher.class);
                contextRefresher.refresh();
            } finally {
                lock.unlock();
                log.debug("Successfully released refresh lock");
            }
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        log.trace("DatabaseContextRefreshMonitor is running ...");
        executorService.scheduleWithFixedDelay(this::apply, 10, 30, TimeUnit.SECONDS);
    }

    @Override
    public void setEnvironment(@Nonnull Environment environment) {
        Objects.requireNonNull(environment);
        if (environment instanceof ConfigurableEnvironment) {
            this.environment = ((ConfigurableEnvironment) environment);
        }
    }

    @Override
    public void setBeanFactory(@Nonnull BeanFactory beanFactory) throws BeansException {
        Objects.requireNonNull(beanFactory);
        if (beanFactory instanceof DefaultListableBeanFactory) {
            this.beanFactory = ((DefaultListableBeanFactory) beanFactory);
        }
    }

    @Override
    public boolean supportsEventType(@Nonnull Class<? extends ApplicationEvent> eventType) {
        Objects.requireNonNull(eventType);
        return EnvironmentChangeEvent.class.isAssignableFrom(eventType)
                || RefreshScopeRefreshedEvent.class.isAssignableFrom(eventType);
    }

    @Override
    public void onApplicationEvent(@Nonnull ApplicationEvent event) {
        log.debug(event instanceof EnvironmentChangeEvent ? "Context is refreshing!" : "Context refresh finished!");
    }
}
