package com.lambda.fusion.core.utils;

import com.lambda.fusion.core.Constants;
import lombok.experimental.UtilityClass;

/**
 * @author Jin
 */
@UtilityClass
public final class ParameterUtils {

    /***
     * 封装模糊查询，自动转义特殊字符
     *
     * @param parameter 查询参数
     * @return 转义后的模糊查询字符串
     */
    public static String fuzzyQuery(String parameter) {
        if (parameter == null) {
            return null;
        }
        // 转义SQL模糊查询特殊字符 % 和 _
        String escaped = parameter.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return Constants.FUZZY + escaped + Constants.FUZZY;
    }
}
