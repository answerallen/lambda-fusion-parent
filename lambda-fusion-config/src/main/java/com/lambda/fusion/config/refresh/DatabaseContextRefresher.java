package com.lambda.fusion.config.refresh;

import static com.lambda.fusion.config.ConfigConstants.ErrorMessages.*;
import static com.lambda.fusion.config.ConfigConstants.Refresh.*;

import com.lambda.fusion.config.datasource.DatabaseBasedPropertySourceLocator;
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
 * 数据库配置变更监听器，每隔30秒检测数据库配置变动并自动刷新上下文
 * 可通过"lambda.fusion.config.auto-refresh.enabled=false"进行关闭
 *
 */
@Slf4j
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class DatabaseContextRefresher
        implements ApplicationRunner, EnvironmentAware, BeanFactoryAware, SmartApplicationListener {

    // 使用常量类中定义的刷新相关常量

    /**
     * 刷新锁，防止并发刷新
     */
    private final Lock refreshLock = new ReentrantLock();

    /**
     * 定时任务执行器
     */
    private static final ScheduledExecutorService EXECUTOR_SERVICE = createExecutorService();

    /**
     * Spring环境配置
     */
    private ConfigurableEnvironment environment;

    /**
     * Spring Bean工厂
     */
    private DefaultListableBeanFactory beanFactory;

    /**
     * 数据库配置源定位器
     */
    private final DatabaseBasedPropertySourceLocator databaseBasedPropertySourceLocator;

    /**
     * 创建定时任务执行器
     *
     * @return 定时任务执行器
     */
    private static ScheduledExecutorService createExecutorService() {
        return new ScheduledThreadPoolExecutor(CORE_POOL_SIZE, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName(THREAD_NAME);
            return thread;
        });
    }

    /**
     * 构造函数
     *
     * @param databaseBasedPropertySourceLocator 数据库配置源定位器
     */
    public DatabaseContextRefresher(DatabaseBasedPropertySourceLocator databaseBasedPropertySourceLocator) {
        this.databaseBasedPropertySourceLocator = Objects.requireNonNull(
                databaseBasedPropertySourceLocator, "databaseBasedPropertySourceLocator cannot be null");
    }

    /**
     * 检查配置变更并应用刷新
     */
    public void apply() {
        if (databaseBasedPropertySourceLocator.changed(environment)) {
            log.debug("Database config data has been changed! Ready to refresh context..");
            doRefresh();
        }
    }

    /**
     * 执行上下文刷新操作
     */
    public void doRefresh() {
        if (refreshLock.tryLock()) {
            log.debug("Successfully acquired refresh lock");
            try {
                final ContextRefresher contextRefresher = beanFactory.getBean(ContextRefresher.class);
                contextRefresher.refresh();
                log.debug("Context refresh completed successfully");
            } catch (Exception e) {
                log.error(FAILED_TO_REFRESH_CONTEXT, e);
            } finally {
                refreshLock.unlock();
                log.debug("Successfully released refresh lock");
            }
        } else {
            log.debug("Refresh already in progress, skipping this attempt");
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("DatabaseContextRefresher is starting up...");
        EXECUTOR_SERVICE.scheduleWithFixedDelay(
                this::apply, INITIAL_DELAY_SECONDS, REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("DatabaseContextRefresher scheduled with {}s initial delay and {}s interval", INITIAL_DELAY_SECONDS, REFRESH_INTERVAL_SECONDS);
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
        if (event instanceof EnvironmentChangeEvent) {
            log.debug("Environment change event received, context is refreshing!");
        } else if (event instanceof RefreshScopeRefreshedEvent) {
            log.debug("Refresh scope refreshed event received, context refresh finished!");
        }
    }
}
