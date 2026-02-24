package com.lambda.fusion.authority.exception;

import com.lambda.cloud.core.exception.model.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Authority模块错误码枚举
 * <p>
 * 定义Authority模块所有业务错误码，统一管理错误信息
 * 错误码规则：Authority模块使用 10000-19999 范围
 *
 * @author fusion
 */
@Getter
@AllArgsConstructor
public enum AuthorityErrorCode implements ErrorCode {

    // ========== 用户相关错误 (10000-10099) ==========
    /**
     * 用户不存在
     */
    USER_NOT_FOUND(10001, "用户不存在"),

    /**
     * 用户名已存在
     */
    USER_NAME_EXISTS(10002, "用户名已存在"),

    /**
     * 用户已被禁用
     */
    USER_DISABLED(10003, "用户已被禁用"),

    /**
     * 用户密码错误
     */
    USER_PASSWORD_ERROR(10004, "用户名或密码错误"),

    /**
     * 用户已被锁定
     */
    USER_LOCKED(10005, "用户已被锁定，请稍后重试"),

    /**
     * 验证码错误
     */
    VERIFY_CODE_ERROR(10006, "验证码错误"),

    /**
     * 验证码已过期
     */
    VERIFY_CODE_EXPIRED(10007, "验证码已过期"),

    /**
     * 用户手机号已存在
     */
    USER_MOBILE_EXISTS(10008, "该手机号已被注册"),

    /**
     * 用户邮箱已存在
     */
    USER_EMAIL_EXISTS(10009, "该邮箱已被注册"),

    /**
     * 原密码错误
     */
    ORIGINAL_PASSWORD_ERROR(10010, "原密码错误"),

    // ========== 角色相关错误 (10100-10199) ==========
    /**
     * 角色不存在
     */
    ROLE_NOT_FOUND(10101, "角色不存在"),

    /**
     * 角色标识已存在
     */
    ROLE_AUTHORITY_EXISTS(10102, "角色标识已存在"),

    /**
     * 角色名称已存在
     */
    ROLE_NAME_EXISTS(10103, "角色名称已存在"),

    /**
     * 角色已分配用户，无法删除
     */
    ROLE_ASSIGNED_TO_USER(10104, "角色已分配用户，无法删除"),

    /**
     * 角色已禁用
     */
    ROLE_DISABLED(10105, "角色已禁用"),

    /**
     * 分组不存在
     */
    GROUP_NOT_FOUND(10106, "分组不存在"),

    /**
     * 分组名称已存在
     */
    GROUP_NAME_EXISTS(10107, "分组名称已存在"),

    // ========== 租户相关错误 (10200-10299) ==========
    /**
     * 租户不存在
     */
    TENANT_NOT_FOUND(10201, "租户不存在"),

    /**
     * 租户已被禁用
     */
    TENANT_DISABLED(10202, "租户已被禁用"),

    /**
     * 租户待审核
     */
    TENANT_PENDING_APPROVAL(10203, "租户待审核"),

    /**
     * 租户审核未通过
     */
    TENANT_REJECTED(10204, "租户审核未通过"),

    /**
     * 租户配置无效
     */
    TENANT_CONFIG_INVALID(10205, "租户配置无效"),

    /**
     * 租户名称已存在
     */
    TENANT_NAME_EXISTS(10206, "租户名称已存在"),

    // ========== 组织相关错误 (10300-10399) ==========
    /**
     * 组织不存在
     */
    ORGANIZATION_NOT_FOUND(10301, "组织不存在"),

    /**
     * 组织名称已存在
     */
    ORGANIZATION_NAME_EXISTS(10302, "组织名称已存在"),

    /**
     * 组织已被禁用
     */
    ORGANIZATION_DISABLED(10303, "组织已被禁用"),

    /**
     * 存在子组织，无法删除
     */
    ORGANIZATION_HAS_CHILDREN(10304, "存在子组织，无法删除"),

    /**
     * 组织下存在用户，无法删除
     */
    ORGANIZATION_HAS_USERS(10305, "组织下存在用户，无法删除"),

    /**
     * 不能移动到自身或子组织
     */
    ORGANIZATION_CANNOT_MOVE_TO_SELF(10306, "不能移动到自身或子组织"),

    // ========== 地区相关错误 (10400-10499) ==========
    /**
     * 地区不存在
     */
    AREA_NOT_FOUND(10401, "地区不存在"),

    /**
     * 地区名称已存在
     */
    AREA_NAME_EXISTS(10402, "地区名称已存在"),

    // ========== 资源相关错误 (10500-10599) ==========
    /**
     * 资源不存在
     */
    RESOURCE_NOT_FOUND(10501, "资源不存在"),

    /**
     * 资源标识已存在
     */
    RESOURCE_AUTHORITY_EXISTS(10502, "资源标识已存在"),

    /**
     * 资源类型不支持
     */
    RESOURCE_TYPE_NOT_SUPPORTED(10503, "资源类型不支持"),

    // ========== 令牌相关错误 (10600-10699) ==========
    /**
     * API令牌不存在
     */
    API_TOKEN_NOT_FOUND(10601, "API令牌不存在"),

    /**
     * API令牌已过期
     */
    API_TOKEN_EXPIRED(10602, "API令牌已过期"),

    /**
     * API令牌已被禁用
     */
    API_TOKEN_DISABLED(10603, "API令牌已被禁用"),

    /**
     * API令牌密钥错误
     */
    API_TOKEN_SECRET_ERROR(10604, "API令牌密钥错误"),

    // ========== 客户端相关错误 (10700-10799) ==========
    /**
     * 客户端不存在
     */
    CLIENT_NOT_FOUND(10701, "客户端不存在"),

    /**
     * 客户端密钥错误
     */
    CLIENT_SECRET_ERROR(10702, "客户端密钥错误"),

    /**
     * 客户端已被禁用
     */
    CLIENT_DISABLED(10703, "客户端已被禁用"),

    // ========== 认证相关错误 (10800-10899) ==========
    /**
     * 用户名或密码错误
     */
    AUTH_USERNAME_OR_PASSWORD_ERROR(10801, "用户名或密码错误"),

    /**
     * 账户已被禁用
     */
    AUTH_ACCOUNT_DISABLED(10802, "账户已被禁用"),

    /**
     * 认证令牌无效
     */
    AUTH_TOKEN_INVALID(10803, "认证令牌无效"),

    /**
     * 认证令牌已过期
     */
    AUTH_TOKEN_EXPIRED(10804, "认证令牌已过期"),

    /**
     * 无访问权限
     */
    AUTH_NO_PERMISSION(10805, "无访问权限"),

    /**
     * 用户不存在
     */
    AUTH_USER_NOT_FOUND(10806, "用户不存在"),

    // ========== 用户中心相关错误 (10900-10999) ==========
    /**
     * 手机号格式错误
     */
    MOBILE_FORMAT_ERROR(10901, "手机号格式错误"),

    /**
     * 邮箱格式错误
     */
    EMAIL_FORMAT_ERROR(10902, "邮箱格式错误"),

    /**
     * 验证码发送失败
     */
    VERIFY_CODE_SEND_FAILED(10903, "验证码发送失败"),

    // ========== 系统错误 (19900-19999) ==========
    /**
     * 系统内部错误
     */
    SYSTEM_ERROR(19900, "系统内部错误"),

    /**
     * 配置错误
     */
    CONFIGURATION_ERROR(19901, "系统配置错误"),

    /**
     * 不支持的操作
     */
    OPERATION_NOT_SUPPORTED(19902, "不支持的操作"),

    /**
     * 参数无效
     */
    INVALID_PARAMETER(19903, "参数无效"),

    /**
     * 并发更新失败
     */
    CONCURRENT_UPDATE_FAILED(19904, "并发更新失败，请重试");

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 错误消息
     */
    private final String message;
}
