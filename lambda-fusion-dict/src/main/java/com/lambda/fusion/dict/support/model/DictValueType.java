package com.lambda.fusion.dict.support.model;

import com.lambda.cloud.core.utils.Assert;
import com.lambda.fusion.dict.support.enums.DictMapper;
import lombok.Getter;

/**
 * @author jin
 */
@Getter
@DictMapper(dictName = "DICT_DATA_TYPE", dictDesc = "字典数据类型", key = "configKey", val = "valueType")
public enum DictValueType {
    /**
     * 静态字典
     */
    STATIC_DICT(0, "静态字典"),
    /**
     * 链接类型
     */
    URL_DICT(1, "HTTP链接"),
    /**
     * SQL类型
     */
    SQL_DICT(2, "SQL"),
    /**
     * 枚举类型
     */
    ENUM_DICT(3, "枚举"),
    ;

    /**
     * 字典值类型
     */
    private final Integer valueType;

    /**
     * 配置参数key
     */
    private final String configKey;

    DictValueType(Integer valueType, String configKey) {
        this.valueType = valueType;
        this.configKey = configKey;
    }

    public static DictValueType getByValueType(Integer valueType) {
        DictValueType type = null;
        for (DictValueType value : values()) {
            if (value.getValueType().equals(valueType)) {
                type = value;
                break;
            }
        }
        Assert.notNull(type, "字典类型未配置");
        return type;
    }
}
