package com.lambda.fusion.core.utils;

import cn.hutool.core.util.StrUtil;
import com.lambda.fusion.core.FusionConstants;
import lombok.experimental.UtilityClass;

/**
 * @author Jin
 */
@UtilityClass
public final class SqlParamUtils {

    /**
     * 默认转义字符
     */
    private static final String ESCAPE_CHAR = "\\";

    /**
     * 封装模糊查询，自动转义特殊字符 (%, _, \)
     */
    public static String fuzzyQuery(String parameter) {
        if (StrUtil.isBlank(parameter)) {
            return null;
        }

        String escaped = parameter
                .replace(ESCAPE_CHAR, ESCAPE_CHAR + ESCAPE_CHAR)
                .replace("%", ESCAPE_CHAR + "%")
                .replace("_", ESCAPE_CHAR + "_");

        return FusionConstants.FUZZY + escaped + FusionConstants.FUZZY;
    }
}
