package com.lambda.fusion.core.annotation;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootApplication
@ComponentScan(basePackages = {"com.lambda.fusion"})
public @interface LambdaFusionApplication {

    @AliasFor(annotation = SpringBootApplication.class, attribute = "scanBasePackages")
    String[] scanBasePackages() default {};

    @AliasFor(annotation = SpringBootApplication.class, attribute = "scanBasePackageClasses")
    Class<?>[] scanBasePackageClasses() default {};

    @AliasFor(annotation = SpringBootApplication.class, attribute = "exclude")
    Class<?>[] exclude() default {};

    @AliasFor(annotation = SpringBootApplication.class, attribute = "excludeName")
    String[] excludeName() default {};

    @AliasFor(annotation = SpringBootApplication.class, attribute = "proxyBeanMethods")
    boolean proxyBeanMethods() default true;

}
