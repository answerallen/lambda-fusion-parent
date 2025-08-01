package com.lambda.fusion.core;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang.StringUtils;

/**
 * Constants
 *
 * @author Jin
 */
public final class Constants {

    private Constants() {}

    public static final String SYSTEM = "system";
    public static final String FUZZY = "%";
    public static final String DELIMITER = ",";
    public static final String SEMICOLON = ";";
    public static final String BEARER = "Bearer ";
    public static final String HMAC = "HmacSHA ";
    public static final String SESSION = "SESSION";
    public static final String JSESSIONID = "JSESSIONID";
    public static final String LOGIN_TIME = "__login-time";
    public static final String LOGIN_USERNAME = "__login-username";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_TYPE_VALUE = "application/json";
    public static final String XML_HTTP_REQUEST = "x-requested-with";
    public static final String XML_HTTP_REQUEST_VALUE = "XMLHttpRequest";
    public static final String AUTHORIZATION = "Authorization";
    public static final String X_CONTENT_TYPE = "X-Content-Type";
    public static final String X_AUTHORIZED_BEARER = Constants.X_AUTHORIZED_TOKEN;
    public static final String X_AUTHORIZED_TOKEN = "x-authorized-token";
    public static final String X_SECURITY_POLICY = "x-security-policy";
    public static final String X_SECURITY_POLICY_CLEAR = "x-security-policy=clear";

    public static final String X_HTTP_METHOD_OVERRIDE = "X-HTTP-Method-Override";
    public static final String REDIRECT_URL = "__redirectUrl";
    public static final String ANCHOR = "#";
    public static final String FAILURE_PAGE = "#/errors/401";
    public static final Charset UTF_8 = StandardCharsets.UTF_8;
    public static final String UTF_8_VALUE = UTF_8.name();
    public static final String EMPTY = StringUtils.EMPTY;
    public static final String SPACE = " ";
    public static final String DOT = ".";
    public static final String JOINER = "-";
    public static final String AT = "@";
    public static final String SLASH = "/";
    public static final String BACK_SLASH = "\\";
    public static final String COLON = ":";
    public static final String UNDERSCORE = "_";
    public static final String ALL = "*";
    public static final String ALL_PATH = "/**";
    public static final String TOKEN_INVALID = "x-token-invalid";

    public static final String ROLE_PREFIX = "ROLE_";
    public static final String ROLE_DEV = ROLE_PREFIX + "DEV";
    public static final String ROLE_ADMIN = ROLE_PREFIX + "ADMIN";
    public static final String ROLE_USER = ROLE_PREFIX + "USER";
    public static final String ROLE_HMAC = ROLE_PREFIX + "HMAC";
    public static final String ROLE_TENANT = ROLE_PREFIX + "TENANT";
    public static final String ROLE_MANAGER = ROLE_PREFIX + "MANAGER";
    public static final String ROLE_SYSTEM = ROLE_PREFIX + "SYSTEM";
    public static final String TENANTID = "tenantid";
    public static final Integer DEFAULT_ENABLE_STATUS_VALUE = 1;
    public static final Integer DEFAULT_DISABLE_STATUS_VALUE = 0;

    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
}
