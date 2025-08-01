package com.lambda.fusion.dict.support.enums;

import java.lang.annotation.*;

/**
 * @author jin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
@Documented
@Inherited
public @interface DictMapper {
    /**
     * @return 字典名称
     */
    String dictName() default "default";

    /**
     * @return 字典描述
     */
    String dictDesc() default "default";

    /**
     * 字典列表名称
     * @return 枚举类中对应列表键的字段
     */
    String key() default "key";

    /**
     * 字典列表值
     * @return 枚举类中对应列表值的字段
     */
    String val() default "val";
}
