package com.lambda.fusion.dict;

/**
 * 字典模块常量定义
 *
 */
public interface DictConstants {

    // ==================== 数据库表名常量 ====================

    /** 字典类型表名 */
     String TABLE_SYS_DICT_TYPE = "la_dict_type";

    /** 字典信息表名 */
     String TABLE_SYS_DICT_INFO = "la_dict_info";


    // ==================== 启用状态常量 ====================

    /** 禁用状态 */
     Integer ENABLE_STATE_DISABLED = 0;

    /** 启用状态 */
     Integer ENABLE_STATE_ENABLED = 1;

    // ==================== 可选择状态常量 ====================

    /** 不可选择（仅显示） */
     Integer SELECTABLE_DISABLED = 0;

    /** 可选择 */
     Integer SELECTABLE_ENABLED = 1;

    // ==================== 层级常量 ====================

    /** 默认层级 */
     Integer DEFAULT_LEVEL = 1;

    /** 根节点父ID */
     String ROOT_PARENT_ID = "0";

    // ==================== SQL解析常量 ====================

    /** SQL解析键名 - KEY */
     String SQL_KEY = "key";

    /** SQL解析键名 - VAL */
     String SQL_VAL = "val";

    /** SQL解析键名 - SEL */
     String SQL_SEL = "sel";

    /** SQL解析键名 - PID */
     String SQL_PID = "pid";

    /** SQL解析键名 - ID */
     String SQL_ID = "id";

    /** SQL解析键名 - RANK */
     String SQL_RANK = "rank";

    // ==================== HTTP协议常量 ====================

    /** HTTP协议 */
     String HTTP_PROTOCOL = "http";

    /** HTTPS协议 */
     String HTTPS_PROTOCOL = "https";


    // ==================== 字段名常量 ====================

    /** 字典名称字段 */
     String FIELD_DICT_NAME = "dictName";

    /** 字段类型字段 */
     String FIELD_FIELD_TYPE = "fieldType";

    /** 启用状态字段 */
     String FIELD_ENABLE_STATE = "enableState";

    /** 租户ID字段 */
     String FIELD_TENANT_ID = "tenantId";

    /** ID字段 */
     String FIELD_ID = "id";

    /** 父级键字段 */
     String FIELD_PARENT_KEYS = "parentKeys";

    /** 层级字段 */
     String FIELD_LEVEL = "level";

    /** 父级ID字段 */
     String FIELD_PARENT_ID = "parentId";

    /** 字典类型字段 */
     String FIELD_DICT_TYPE = "dictType";

    /** 字段名字段 */
     String FIELD_FIELD_NAME = "fieldName";

    /** 可选择字段 */
     String FIELD_SELECTABLE = "selectable";

    /** 字典信息ID字段 */
     String FIELD_DICT_INFO_ID = "dictInfoId";


    // ==================== 错误消息常量 ====================

    /** 字典HTTP请求错误消息 */
     String ERROR_DICT_NOT_NULL= "字典不能为空";

    /** SQL解析异常错误消息 */
     String ERROR_JSQL_PARSER_EXCEPTION = "SQL解析异常错误";

    /** 字典HTTP请求错误消息 */
     String ERROR_DICT_HTTP_REQUEST = "字典http请求错误";

    /** 字典类型不能为空 */
     String MSG_DICT_TYPE_NOT_EMPTY = "字典类型不能为空";

    /** 字典字段类型不能为空 */
     String MSG_DICT_FIELD_TYPE_NOT_EMPTY = "字典字段类型不能为空";

    /** 字典字段名不能为空 */
     String MSG_DICT_FIELD_NAME_NOT_EMPTY = "字典字段名不能为空";

    /** 字典排序号不能为空 */
     String MSG_DICT_SORT_NUMBER_NOT_EMPTY = "字典排序号不能为空";

    /** 字典启用状态不能为空 */
     String MSG_DICT_ENABLED_NOT_EMPTY = "字典启用状态不能为空";

    /** 字典ID不能为空 */
     String MSG_DICT_ID_NOT_EMPTY = "字典ID不能为空";

    /** 字典更新数据不存在 */
     String MSG_DICT_UPDATE_DATA_NOT_EXISTED = "字典更新数据不存在";

    /** 字典类型已存在子类型 */
     String MSG_DICT_EXISTED_CHILD_TYPE = "字典类型已存在子类型";

    /** 字典类型不存在 */
     String MSG_DICT_TYPE_NOT_EXISTED = "字典类型不存在";

    /** 字典类型已存在 */
     String MSG_DICT_TYPE_EXISTED = "字典类型已存在";

    /** 字典名称不能为空 */
     String MSG_DICT_NAME_NOT_EMPTY = "字典名称不能为空";

    // ==================== 其他常量 ====================

    /** 默认字典名称 */
     String DEFAULT_DICT_NAME = "default";

    /** 默认包名 */
     String DEFAULT_PACKAGE = "com.lambda.fusion";

    /** 父级键截取长度 */
     int PARENT_KEY_SUBSTRING_LENGTH = 8;
}
