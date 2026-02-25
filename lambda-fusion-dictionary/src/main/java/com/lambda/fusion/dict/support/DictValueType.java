package com.lambda.fusion.dict.support;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.fusion.core.annotation.DictMapper;
import com.lambda.fusion.core.enums.DictEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jin
 */
@Getter
@AllArgsConstructor
@DictMapper(dictName = "DICT_DATA_TYPE", dictUsage = 0, dictDesc = "字典数据类型")
public enum DictValueType implements DictEnum<Integer> {
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
    ENUM_DICT(3, "枚举");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String label;

    public static DictValueType of(Integer valueType) {
        if (valueType == null) {
            return null;
        }
        DictValueType type = null;
        for (DictValueType value : values()) {
            if (value.getCode().equals(valueType)) {
                type = value;
                break;
            }
        }
        Assert.notNull(type, "字典类型未配置");
        return type;
    }

    public boolean isEnumDict() {
        return DictValueType.ENUM_DICT.equals(this);
    }

    public boolean isNotEnumDict() {
        return !DictValueType.ENUM_DICT.equals(this);
    }
}
