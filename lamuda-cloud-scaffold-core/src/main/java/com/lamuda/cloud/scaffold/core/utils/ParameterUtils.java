package com.lamuda.cloud.scaffold.core.utils;


import com.lamuda.cloud.scaffold.core.Constants;

/**
 * @author Jin
 */
public final class ParameterUtils {
    private ParameterUtils() {
    }

    /***
     * 封装模糊查询
     * @param parameter
     * @return java.lang.String
     */
    public static String fuzzyQuery(String parameter) {
        return Constants.FUZZY + parameter + Constants.FUZZY;
    }
}
