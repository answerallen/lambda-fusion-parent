package com.lambda.fusion.dict.common.constants;

/**
 * 字典模块常量定义
 *
 */
public final class DictConstants {

    private DictConstants() {
        // 防止实例化
    }

    // ==================== 数据库表名常量 ====================

    /** 字典类型表名 */
    public static final String TABLE_SYS_DICT_TYPE = "sys_dict_type";

    /** 字典信息表名 */
    public static final String TABLE_SYS_DICT_INFO = "sys_dict_info";

    // ==================== 字典数据类型常量 ====================

    /** 静态字典类型 */
    public static final Integer DATA_TYPE_STATIC = 0;

    /** URL字典类型 */
    public static final Integer DATA_TYPE_URL = 1;

    /** SQL字典类型 */
    public static final Integer DATA_TYPE_SQL = 2;

    /** 枚举字典类型 */
    public static final Integer DATA_TYPE_ENUM = 3;

    // ==================== 字典使用类型常量 ====================

    /** 系统字典 */
    public static final Integer DICT_USAGE_SYSTEM = 0;

    /** 用户字典 */
    public static final Integer DICT_USAGE_USER = 1;

    // ==================== 启用状态常量 ====================

    /** 禁用状态 */
    public static final Integer ENABLE_STATE_DISABLED = 0;

    /** 启用状态 */
    public static final Integer ENABLE_STATE_ENABLED = 1;

    // ==================== 可选择状态常量 ====================

    /** 不可选择（仅显示） */
    public static final Integer SELECTABLE_DISABLED = 0;

    /** 可选择 */
    public static final Integer SELECTABLE_ENABLED = 1;

    // ==================== 层级常量 ====================

    /** 默认层级 */
    public static final Integer DEFAULT_LEVEL = 1;

    /** 根节点父ID */
    public static final String ROOT_PARENT_ID = "0";

    // ==================== SQL解析常量 ====================

    /** SQL解析键名 - KEY */
    public static final String SQL_KEY = "key";

    /** SQL解析键名 - VAL */
    public static final String SQL_VAL = "val";

    /** SQL解析键名 - SEL */
    public static final String SQL_SEL = "sel";

    /** SQL解析键名 - PID */
    public static final String SQL_PID = "pid";

    /** SQL解析键名 - ID */
    public static final String SQL_ID = "id";

    /** SQL解析键名 - RANK */
    public static final String SQL_RANK = "rank";

    // ==================== HTTP协议常量 ====================

    /** HTTP协议 */
    public static final String HTTP_PROTOCOL = "http";

    /** HTTPS协议 */
    public static final String HTTPS_PROTOCOL = "https";

    // ==================== 分页常量 ====================

    /** 默认页码 */
    public static final String DEFAULT_PAGE_NUMBER = "1";

    /** 默认页大小 */
    public static final String DEFAULT_PAGE_SIZE = "20";

    // ==================== 字段名常量 ====================

    /** 字典名称字段 */
    public static final String FIELD_DICT_NAME = "dictName";

    /** 字段类型字段 */
    public static final String FIELD_FIELD_TYPE = "fieldType";

    /** 启用状态字段 */
    public static final String FIELD_ENABLE_STATE = "enableState";

    /** 租户ID字段 */
    public static final String FIELD_TENANT_ID = "tenantId";

    /** ID字段 */
    public static final String FIELD_ID = "id";

    /** 父级键字段 */
    public static final String FIELD_PARENT_KEYS = "parentkeys";

    /** 层级字段 */
    public static final String FIELD_LEVEL = "level";

    /** 父级ID字段 */
    public static final String FIELD_PARENT_ID = "parentId";

    /** 字典类型字段 */
    public static final String FIELD_DICT_TYPE = "dictType";

    /** 字段名字段 */
    public static final String FIELD_FIELD_NAME = "fieldName";

    /** 可选择字段 */
    public static final String FIELD_SELECTABLE = "selectable";

    /** 字典信息ID字段 */
    public static final String FIELD_DICT_INFO_ID = "dictInfoId";

    // ==================== 状态常量别名 ====================

    /** 可选择状态启用 */
    public static final Integer SELECTABLE_STATE_ENABLED = SELECTABLE_ENABLED;

    /** 可选择状态禁用 */
    public static final Integer SELECTABLE_STATE_DISABLED = SELECTABLE_DISABLED;

    // ==================== 错误消息常量 ====================

    /** 非法SQL错误消息 */
    public static final String ERROR_ILLEGAL_SQL = "Illegal SQL";

    /** SQL解析异常错误消息 */
    public static final String ERROR_JSQL_PARSER_EXCEPTION = "JSQLParserException";

    /** 字典HTTP请求错误消息 */
    public static final String ERROR_DICT_HTTP_REQUEST = "字典http请求错误";

    // ==================== 国际化消息键常量 ====================

    /** 字典类型不能为空 */
    public static final String MSG_DICT_TYPE_NOT_EMPTY = "fx.dictionary.dict.type.notempty";

    /** 字典字段类型不能为空 */
    public static final String MSG_DICT_FIELD_TYPE_NOT_EMPTY = "fx.dictionary.dict.fieldtype.notempty";

    /** 字典字段名不能为空 */
    public static final String MSG_DICT_FIELD_NAME_NOT_EMPTY = "fx.dictionary.dict.field.name.notempty";

    /** 字典排序号不能为空 */
    public static final String MSG_DICT_SORT_NUMBER_NOT_EMPTY = "fx.dictionary.dict.sort.number.notempty";

    /** 字典启用状态不能为空 */
    public static final String MSG_DICT_ENABLED_NOT_EMPTY = "fx.dictionary.dict.enabled.notempty";

    /** 字典ID不能为空 */
    public static final String MSG_DICT_ID_NOT_EMPTY = "fx.dictionary.dict.id.notempty";

    /** 字典更新数据不存在 */
    public static final String MSG_DICT_UPDATE_DATA_NOT_EXISTED = "fx.dictionary.dict.update.data.not.existed";

    /** 字典类型已存在子类型 */
    public static final String MSG_DICT_EXISTED_CHILD_TYPE = "fx.dictionary.dict.existed.child.type";

    /** 字典类型不存在 */
    public static final String MSG_DICT_TYPE_NOT_EXISTED = "fx.dictionary.dict.type.not.existed";

    /** 字典类型已存在 */
    public static final String MSG_DICT_TYPE_EXISTED = "fx.dictionary.dict.type.existed";

    /** 字典名称不能为空 */
    public static final String MSG_DICT_NAME_NOT_EMPTY = "fx.dictionary.dict.name.notempty";

    // ==================== 其他常量 ====================

    /** 默认字典名称 */
    public static final String DEFAULT_DICT_NAME = "default";

    /** 父级键截取长度 */
    public static final int PARENT_KEY_SUBSTRING_LENGTH = 8;
}
