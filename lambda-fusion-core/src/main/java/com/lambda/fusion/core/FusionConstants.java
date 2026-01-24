package com.lambda.fusion.core;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang.StringUtils;

/**
 * 常量定义类
 * 集中管理系统中使用的所有常量，按功能模块分组组织
 *
 * @author Jin
 */
@UtilityClass
public final class FusionConstants {

    // ========== 系统基础常量 ==========
    /** 启用 **/
    public static final Integer ENABLED = 1;
    /** 停用 **/
    public static final Integer DISABLED = 0;
    /** 系统标识 */
    public static final String SYSTEM = "system";
    /** 设备终端 */
    public static final String DEVICE_PC_WEB = "pc-web";
    /** 租户ID 字段名 */
    public static final String TENANT_ID = "tenant_id";
    /** 租户域名 Redis Key */
    public static final String TENANT_HOST_REDIS_KEY = "tenant_host";
    /** 日期时间格式 */
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    // ========== 字符串分隔符常量 ==========
    /** 分隔符0 */
    public static final String SEPARATOR0 = "-";
    /** 模糊查询通配符 */
    public static final String FUZZY = "%";
    /** 逗号分隔符 */
    public static final String DELIMITER = ",";
    /** 分号分隔符 */
    public static final String SEMICOLON = ";";
    /** 空字符串 */
    public static final String EMPTY = StringUtils.EMPTY;
    /** 空格 */
    public static final String SPACE = " ";
    /** 点号 */
    public static final String DOT = ".";
    /** 连接符 */
    public static final String JOINER = "-";
    /** @ 符号 */
    public static final String AT = "@";
    /** 正斜杠 */
    public static final String SLASH = "/";
    /** 反斜杠 */
    public static final String BACK_SLASH = "\\";
    /** 冒号 */
    public static final String COLON = ":";
    /** 下划线 */
    public static final String UNDERSCORE = "_";
    /** 井号 */
    public static final String ANCHOR = "#";
    /** 通配符 */
    public static final String ALL = "*";
    /** 通配符路径 */
    public static final String ALL_PATH = "/**";

    // ========== HTTP相关常量 ==========
    /** Bearer 认证前缀 */
    public static final String BEARER = "Bearer ";
    /** HMAC 算法前缀 */
    public static final String HMAC = "HmacSHA ";
    /** Content-Type 请求头 */
    public static final String CONTENT_TYPE = "Content-Type";
    /** JSON Content-Type 值 */
    public static final String CONTENT_TYPE_VALUE = "application/json";
    /** XMLHttpRequest 请求头 */
    public static final String XML_HTTP_REQUEST = "x-requested-with";
    /** XMLHttpRequest 请求头值 */
    public static final String XML_HTTP_REQUEST_VALUE = "XMLHttpRequest";
    /** Authorization 请求头 */
    public static final String AUTHORIZATION = "Authorization";
    /** X-Content-Type 请求头 */
    public static final String X_CONTENT_TYPE = "X-Content-Type";
    /** 自定义认证Token请求头 */
    public static final String X_AUTHORIZED_TOKEN = "x-authorized-token";
    /** 自定义认证Bearer请求头 */
    public static final String X_AUTHORIZED_BEARER = X_AUTHORIZED_TOKEN;
    /** 安全策略请求头 */
    public static final String X_SECURITY_POLICY = "x-security-policy";
    /** 清除安全策略请求头值 */
    public static final String X_SECURITY_POLICY_CLEAR = "x-security-policy=clear";
    /** HTTP方法覆盖请求头 */
    public static final String X_HTTP_METHOD_OVERRIDE = "X-HTTP-Method-Override";
    /** Token无效标识 */
    public static final String TOKEN_INVALID = "x-token-invalid";
    /** UTF-8 字符集 */
    public static final Charset UTF_8 = StandardCharsets.UTF_8;
    /** UTF-8 字符集名称 */
    public static final String UTF_8_VALUE = UTF_8.name();

    // ========== 会话相关常量 ==========
    /** 会话标识 */
    public static final String SESSION = "SESSION";
    /** JSESSIONID */
    public static final String JSESSIONID = "JSESSIONID";
    /** 登录时间键 */
    public static final String LOGIN_TIME = "__login-time";
    /** 登录用户名键 */
    public static final String LOGIN_USERNAME = "__login-username";
    /** 重定向URL键 */
    public static final String REDIRECT_URL = "__redirectUrl";
    /** 失败页面路径 */
    public static final String FAILURE_PAGE = "#/errors/401";

    // ========== 角色权限常量 ==========
    /** 角色前缀 */
    public static final String ROLE_PREFIX = "ROLE_";
    /** 开发者角色 */
    public static final String ROLE_DEV = ROLE_PREFIX + "DEV";
    /** 管理员角色 */
    public static final String ROLE_ADMIN = ROLE_PREFIX + "ADMIN";
    /** 普通用户角色 */
    public static final String ROLE_USER = ROLE_PREFIX + "USER";
    /** HMAC角色 */
    public static final String ROLE_HMAC = ROLE_PREFIX + "HMAC";
    /** 租户角色 */
    public static final String ROLE_TENANT = ROLE_PREFIX + "TENANT";
    /** 管理者角色 */
    public static final String ROLE_MANAGER = ROLE_PREFIX + "MANAGER";
    /** 租户管理员角色 */
    public static final String ROLE_TENANT_MANAGER = ROLE_PREFIX + "TENANT_MANAGER";
    /** 系统角色 */
    public static final String ROLE_SYSTEM = ROLE_PREFIX + "SYSTEM";

    // ========== 树形结构相关常量 ==========
    /** 树结构顶级节点标识 */
    public static final String TREE_TOP_LEVEL = "";
    /** 树结构分隔符 */
    public static final String TREE_SPLIT = "-";
}
