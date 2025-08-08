package com.lambda.fusion.authority.utils;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.ExceptionUtils;
import com.baomidou.mybatisplus.extension.service.IService;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import lombok.SneakyThrows;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.MyBatisExceptionTranslator;
import org.mybatis.spring.SqlSessionHolder;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class MybatisUtils implements ApplicationContextAware {

    private static SqlSessionFactory sqlSessionFactory;

    private MybatisUtils() {}

    public static <T, V> void batchInsert(Set<T> target, Class<V> tClass, BiConsumer<T, V> consumer) {
        if (CollectionUtils.isEmpty(target)) {
            return;
        }
        batchInsert(new ArrayList<>(target), tClass, consumer);
    }

    @SneakyThrows
    public static <T, V> void batchInsert(List<T> target, Class<V> tClass, BiConsumer<T, V> consumer) {
        SqlSessionHolder sqlSessionHolder =
                (SqlSessionHolder) TransactionSynchronizationManager.getResource(sqlSessionFactory);
        boolean transaction = TransactionSynchronizationManager.isSynchronizationActive();
        if (sqlSessionHolder != null) {
            SqlSession sqlSession = sqlSessionHolder.getSqlSession();
            // 原生无法支持执行器切换，当存在批量操作时，会嵌套两个session的，优先commit上一个session
            // 按道理来说，这里的值应该一直为false。
            sqlSession.commit(!transaction);
        }
        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
        V mapper = sqlSession.getMapper(tClass);
        try {
            if (CollectionUtils.isNotEmpty(target)) {
                List<List<T>> partition = Lists.partition(target, IService.DEFAULT_BATCH_SIZE);
                partition.stream().flatMap(Collection::stream).forEach(t -> consumer.accept(t, mapper));
            }
            // 非事务情况下，强制commit。
            sqlSession.commit(!transaction);
        } catch (Exception t) {
            sqlSession.rollback();
            Throwable unwrapped = ExceptionUtil.unwrapThrowable(t);
            if (unwrapped instanceof PersistenceException) {
                MyBatisExceptionTranslator myBatisExceptionTranslator = new MyBatisExceptionTranslator(
                        sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(), true);
                Throwable throwable =
                        myBatisExceptionTranslator.translateExceptionIfPossible((PersistenceException) unwrapped);
                if (throwable != null) {
                    throw throwable;
                }
            }
            throw ExceptionUtils.mpe(unwrapped);
        } finally {
            sqlSession.close();
        }
    }

    private static void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
        MybatisUtils.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        SqlSessionFactory globalFactory = applicationContext.getBean(SqlSessionFactory.class);
        setSqlSessionFactory(globalFactory);
    }
}
