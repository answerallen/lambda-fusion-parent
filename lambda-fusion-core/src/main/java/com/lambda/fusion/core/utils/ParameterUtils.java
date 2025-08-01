package com.lambda.fusion.core.utils;

import com.lambda.fusion.core.Constants;

/**
 * @author Jin
 */
public final class ParameterUtils {
    private ParameterUtils() {}

    /***
     * 封装模糊查询
     * @param parameter
     * @return java.lang.String
     */
    public static String fuzzyQuery(String parameter) {
        return Constants.FUZZY + parameter + Constants.FUZZY;
    }
}
