package com.lamuda.cloud.scaffold.dict.support.enums;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * @author Jin
 */
@Slf4j
public class DictScanner extends ClassPathBeanDefinitionScanner {
    public DictScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters) {
        super(registry, useDefaultFilters);
    }

    @Nonnull
    @Override
    protected Set<BeanDefinitionHolder> doScan(@Nonnull String... basePackages) {
        Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);
        if (beanDefinitions.isEmpty()) {
            log.warn("没有在 {} 找到相关类", Arrays.toString(basePackages));
            return Collections.emptySet();
        }
        dictScan(beanDefinitions);
        return Collections.emptySet();
    }

    private void dictScan(Set<BeanDefinitionHolder> beanDefinitions) {
        for (BeanDefinitionHolder holder : beanDefinitions) {
            final GenericBeanDefinition genericBeanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            final String beanClassName = genericBeanDefinition.getBeanClassName();
            final Class<?> aClass;
            try {
                aClass = Class.forName(beanClassName);
                if (aClass.isEnum()) {
                    processEnum(beanClassName, aClass);
                }
            } catch (Exception e) {
                log.warn("解析枚举字典失败", e);
            }
        }
    }

    @SuppressWarnings("squid:S3011")
    private void processEnum(String beanClassName, Class<?> aClass) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        final DictMapper annotation = AnnotationUtils.findAnnotation(aClass, DictMapper.class);
        if (Objects.isNull(annotation)) {
            log.warn("没有配置注解,跳过执行: {}", beanClassName);
            return;
        }
        // 字典类型
        final String dictName = annotation.dictName();
        final String dictDesc = annotation.dictDesc();
        // 字典选项
        final String key = annotation.key();
        final String val = annotation.val();

        final DictHolder holders = DictContextHolders.MAPPER_HOLDERS.getOrDefault(dictName, new DictHolder(dictName, dictDesc));
        final Method values = aClass.getMethod("values");
        final Object[] invoke = (Object[]) values.invoke(null);
        final Field keyField = ReflectionUtils.findField(aClass, key);
        final Field valField = ReflectionUtils.findField(aClass, val);
        if (Objects.nonNull(keyField) && Objects.nonNull(valField)) {
            keyField.setAccessible(true);
            valField.setAccessible(true);
            for (Object obj : invoke) {
                holders.addOption(keyField.get(obj).toString(), valField.get(obj));
            }
            DictContextHolders.addDictHolder(holders);
        }
    }

    @Override
    protected void registerBeanDefinition(@NonNull BeanDefinitionHolder definitionHolder, @NonNull BeanDefinitionRegistry registry) {
        // 不需要注册到容器里
    }
}
