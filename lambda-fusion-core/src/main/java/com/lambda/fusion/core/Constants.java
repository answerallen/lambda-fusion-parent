package com.lambda.fusion.core;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang.StringUtils;

/**
 * Lambda Fusion Core 常量定义类
 * 集中管理系统中使用的所有常量，按功能模块分组组织
 *
 * @author Jin
 */
public final class Constants {

    private Constants() {}

    // ========== 系统基础常量 ==========
    /** 系统标识 */
    public static final String SYSTEM = "system";
    /** 租户ID字段名 */
    public static final String TENANT_ID = "tenant_id";
    /** 默认启用状态值 */
    public static final Integer DEFAULT_ENABLE_STATUS_VALUE = 1;
    /** 默认禁用状态值 */
    public static final Integer DEFAULT_DISABLE_STATUS_VALUE = 0;
    /** 日期时间格式 */
    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    // ========== 字符串分隔符常量 ==========
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
    /** 系统角色 */
    public static final String ROLE_SYSTEM = ROLE_PREFIX + "SYSTEM";

    // ========== 分页相关常量 ==========
    /** 页码不能为空的错误消息 */
    public static final String MSG_PAGE_NUM_NOT_NULL = "pageNum不能为空";
    /** 页面大小不能为空的错误消息 */
    public static final String MSG_PAGE_SIZE_NOT_NULL = "pageSize不能为空";

    // ========== 树形结构相关常量 ==========
    /** 树结构顶级节点标识 */
    public static final String TREE_TOP_LEVEL = "";
    /** 树结构分隔符 */
    public static final String TREE_SPLIT = "-";

    // ========== 事件相关常量 ==========
    /** 消息事件无接收者错误信息 */
    public static final String MSG_MESSAGE_EVENT_NO_RECEIVER = "fx.message.client.event.not.receiver";

    // ========== 集合初始化容量常量 ==========
    /** 默认HashSet初始容量 */
    public static final int DEFAULT_HASH_SET_CAPACITY = 16;
    /** 默认HashMap初始容量 */
    public static final int DEFAULT_HASH_MAP_CAPACITY = 8;

    // ========== 拖拽模式相关常量 ==========
    /** 拖拽模式错误索引消息 */
    public static final String MSG_DRAG_MODE_WRONG_INDEX = "wrong index of DragMode, allowed: [0, 1, 2]";

    // ========== 日志消息模板常量 ==========
    /** 树构建时间日志消息模板 */
    public static final String LOG_TREE_BUILD_TIME = "build tree cast: {}ns";
    /** 过滤数据异常日志消息模板 */
    public static final String LOG_FILTER_DATA_EXCEPTION = "过滤数据发生异常,过滤参数,{}";

    // ========== Schema描述常量 ==========

    // 基础Schema字段
    /** Schema代码字段 */
    public static final String SCHEMA_CODE = "CODE";
    /** Schema发送人字段 */
    public static final String SCHEMA_SENDER = "发送人";
    /** Schema通知内容字段 */
    public static final String SCHEMA_CONTENT = "通知内容";
    /** Schema触发时间字段 */
    public static final String SCHEMA_TRIGGER_TIME = "触发时间";
    /** Schema业务唯一标识字段 */
    public static final String SCHEMA_BUSINESS_KEY = "业务唯一标识";
    /** Schema输入参数字段 */
    public static final String SCHEMA_INPUTS = "输入参数";

    // 通知相关Schema字段
    /** Schema通知对象类型字段 */
    public static final String SCHEMA_RECEIVER_TYPE = "通知对象类型 1-角色 2-用户 4-无 5-所有用户";
    /** Schema通知对象参数字段 */
    public static final String SCHEMA_RECEIVER_TARGET = "通知对象参数";

    // 字典相关Schema字段
    /** Schema动态字典字段 */
    public static final String SCHEMA_DYNAMIC_DICT = "动态字典";
    /** Schema展示参数字段 */
    public static final String SCHEMA_DISPLAY_PARAM = "展示参数, 页面展示使用";
    /** Schema映射参数字段 */
    public static final String SCHEMA_MAPPING_PARAM = "映射参数, 持久化时使用";
    /** Schema可选择状态字段 */
    public static final String SCHEMA_SELECTABLE_STATE = "可以被选择状态。0：只能用作显示，不能用于下拉选择，1：可以显示和下拉选择";

    // 树形结构Schema字段
    /** Schema父级节点字段 */
    public static final String SCHEMA_PARENT_NODE = "父级节点";
    /** Schema节点ID字段 */
    public static final String SCHEMA_NODE_ID = "节点id";
    /** Schema级别字段 */
    public static final String SCHEMA_LEVEL = "级别：最顶层为1，后边层数累加";
    /** Schema隐藏级别字段 */
    public static final String SCHEMA_LEVEL_HIDDEN = "级别：最顶层为1，后边层数累加";
}
