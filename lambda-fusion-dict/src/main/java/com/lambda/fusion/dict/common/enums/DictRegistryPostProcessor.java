package com.lambda.fusion.dict.common.enums;

import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

/**
 * 将编码字典暴露成API，有2种使用方式：<br/>
 * 1 枚举类使用方式参考 {@link DictValueType}；<br/>
 * 2 接口实现方式：<br/>
 * <pre>
 *     {@code
 *@Component
 * @IgnoreFromGeneratedReport
 * public class DynamicDictServiceImpl implements DictFactory {
 *     @Override
 *     public DictHolder getDictHolder() {
 *         DictHolder dictHolder = new DictHolder("beanDy", "接口注册测试");
 *         dictHolder.addOption(new DynamicDict("测试1", "test1"));
 *         dictHolder.addOption(new DynamicDict("测试2", "test2"));
 *         return dictHolder;
 *     }
 *   }
 * }
 * </pre>
 *
 * @see DictFactory
 * @author Jin
 */
@Slf4j
@Component
public class DictRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor, BeanPostProcessor {
    @Override
    public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry registry) throws BeansException {
        final DictScanner scanner = new DictScanner(registry, false);
        List<String> packages = new ArrayList<>();
        if (registry instanceof DefaultListableBeanFactory beanFactory) {
            packages.addAll(AutoConfigurationPackages.get(beanFactory));
        } else {
            packages.add("com.lambda.cloud");
        }
        scanner.setIncludeAnnotationConfig(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(DictMapper.class));
        scanner.scan(packages.toArray(new String[0]));
    }

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        if (bean instanceof DictFactory dictFactory) {
            final DictHolder dictHolder = dictFactory.getDictHolder();
            DictContextHolders.MAPPER_HOLDERS.put(dictHolder.getDictName(), dictHolder);
            log.info("process {} success.", bean.getClass());
        }
        return bean;
    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // Noting to do
    }
}
