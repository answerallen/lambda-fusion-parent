package com.lambda.fusion.config.refresh;

import static com.lambda.fusion.config.ConfigConstants.THREAD_NAME;

import com.lambda.fusion.config.ConfigProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
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
import org.springframework.context.event.SmartApplicationListener;

/**
 * 数据库配置变更监听器，每隔30秒检测数据库配置变动并自动刷新上下文
 * 可通过"lambda.fusion.config.auto-refresh.enabled=false"进行关闭
 *
 */
@Slf4j
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class DatabaseContextRefresher implements ApplicationRunner, BeanFactoryAware, SmartApplicationListener {

    // 使用常量类中定义的刷新相关常量

    /**
     * 刷新锁，防止并发刷新
     */
    private final Lock refreshLock = new ReentrantLock();

    /**
     * 定时任务执行器
     */
    private final ScheduledExecutorService executorService;

    private final ConfigProperties.AutoRefresh autoRefresh;

    /**
     * Spring Bean工厂
     */
    private DefaultListableBeanFactory beanFactory;

    /**
     * 数据库配置监视器
     */
    private final DatabaseConfigWatcher databaseConfigWatcher;

    /**
     * 创建定时任务执行器
     *
     * @return 定时任务执行器
     */
    private ScheduledExecutorService createExecutorService(int corePoolSize) {
        return new ScheduledThreadPoolExecutor(corePoolSize, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName(THREAD_NAME);
            return thread;
        });
    }

    /**
     * 构造函数
     *
     * @param databaseConfigWatcher 数据库配置监视器
     */
    public DatabaseContextRefresher(DatabaseConfigWatcher databaseConfigWatcher, ConfigProperties configProperties) {
        this.databaseConfigWatcher =
                Objects.requireNonNull(databaseConfigWatcher, "databaseConfigWatcher cannot be null");
        ConfigProperties checked = Objects.requireNonNull(configProperties, "configProperties cannot be null");
        this.autoRefresh = checked.getAutoRefresh();
        this.executorService = createExecutorService(autoRefresh.getCorePoolSize());
    }

    /**
     * 检查配置变更并应用刷新
     */
    public void refresh() {
        if (databaseConfigWatcher.changed()) {
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
                log.error("Failed to refresh context", e);
            } finally {
                refreshLock.unlock();
                log.debug("Successfully released refresh lock");
            }
        } else {
            log.debug("Refresh already in progress, skipping this attempt");
        }
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        log.info("DatabaseContextRefresher is starting up...");
        executorService.scheduleWithFixedDelay(
                this::refresh,
                autoRefresh.getInitialDelaySeconds(),
                autoRefresh.getIntervalSeconds(),
                TimeUnit.SECONDS);
        log.info(
                "DatabaseContextRefresher scheduled with {}s initial delay and {}s interval",
                autoRefresh.getInitialDelaySeconds(),
                autoRefresh.getIntervalSeconds());
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
