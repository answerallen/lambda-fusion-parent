package com.lambda.fusion.authority.exception;

import com.lambda.cloud.core.exception.model.ErrorCode;
import com.lambda.cloud.mvc.execption.BusinessException;

/**
 * Authority 模块业务异常
 * <p>
 * 继承自统一的BusinessException，用于Authority模块的业务异常处理
 * 提供便捷的构造方法，支持错误码和参数化消息
 *
 * @author Jin
 */
public class AuthorityBusinessException extends BusinessException {

    /**
     * 使用错误码构造异常
     *
     * @param errorCode 错误码枚举
     */
    public AuthorityBusinessException(ErrorCode errorCode) {
        super(errorCode);
    }

    /**
     * 使用错误码和参数构造异常
     * <p>
     * 支持消息模板参数化，例如：
     * <pre>
     * throw new AuthorityBusinessException(AuthorityErrorCode.USER_NOT_FOUND, userId);
     * </pre>
     *
     * @param errorCode 错误码枚举
     * @param args      消息参数
     */
    public AuthorityBusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    /**
     * 使用错误码和原始异常构造异常
     * <p>
     * 用于包装底层异常，保留异常堆栈信息
     *
     * @param errorCode 错误码枚举
     * @param cause     原始异常
     */
    public AuthorityBusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getCode(), errorCode.getMessage(), null, cause);
    }

    /**
     * 使用错误码、参数和原始异常构造异常
     *
     * @param errorCode 错误码枚举
     * @param cause     原始异常
     * @param args      消息参数
     */
    public AuthorityBusinessException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.getCode(), errorCode.getMessage(), args, cause);
    }

    // ========== 用户相关便捷方法 ==========

    /**
     * 便捷方法：用户名不存在异常
     *
     * @param username 用户名
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException userNotFound(String username) {
        return new AuthorityBusinessException(AuthorityErrorCode.USER_NOT_FOUND, username);
    }

    /**
     * 便捷方法：用户名已存在异常
     *
     * @param username 用户名
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException userNameExists(String username) {
        return new AuthorityBusinessException(AuthorityErrorCode.USER_NAME_EXISTS, username);
    }

    /**
     * 便捷方法：手机号不存在异常
     *
     * @param mobile 手机号
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException userMobileNotFound(String mobile) {
        return new AuthorityBusinessException(AuthorityErrorCode.USER_MOBILE_NOT_FOUND, mobile);
    }

    /**
     * 便捷方法：用户手机号已存在异常
     *
     * @param mobile 手机号
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException userMobileExists(String mobile) {
        return new AuthorityBusinessException(AuthorityErrorCode.USER_MOBILE_EXISTS, mobile);
    }

    /**
     * 便捷方法：用户邮箱已存在异常
     *
     * @param email 邮箱
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException userEmailExists(String email) {
        return new AuthorityBusinessException(AuthorityErrorCode.USER_EMAIL_EXISTS, email);
    }

    /**
     * 便捷方法：原密码错误异常
     *
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException originalPasswordError() {
        return new AuthorityBusinessException(AuthorityErrorCode.ORIGINAL_PASSWORD_ERROR);
    }

    // ========== 角色相关便捷方法 ==========

    /**
     * 便捷方法：角色不存在异常
     *
     * @param roleId 角色ID
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException roleNotFound(String roleId) {
        return new AuthorityBusinessException(AuthorityErrorCode.ROLE_NOT_FOUND, roleId);
    }

    /**
     * 便捷方法：角色名称已存在异常
     *
     * @param roleName 角色名称
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException roleNameExists(String roleName) {
        return new AuthorityBusinessException(AuthorityErrorCode.ROLE_NAME_EXISTS, roleName);
    }

    /**
     * 便捷方法：角色已分配用户，无法删除异常
     *
     * @param roleId 角色ID
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException roleAssignedToUser(String roleId) {
        return new AuthorityBusinessException(AuthorityErrorCode.ROLE_ASSIGNED_TO_USER, roleId);
    }

    // ========== 租户相关便捷方法 ==========

    /**
     * 便捷方法：租户不存在异常
     *
     * @param tenantId 租户ID
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException tenantNotFound(String tenantId) {
        return new AuthorityBusinessException(AuthorityErrorCode.TENANT_NOT_FOUND, tenantId);
    }

    /**
     * 便捷方法：租户已被禁用异常
     *
     * @param tenantId 租户ID
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException tenantDisabled(String tenantId) {
        return new AuthorityBusinessException(AuthorityErrorCode.TENANT_DISABLED, tenantId);
    }

    // ========== 组织相关便捷方法 ==========

    /**
     * 便捷方法：组织不存在异常
     *
     * @param orgId 组织ID
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException organizationNotFound(String orgId) {
        return new AuthorityBusinessException(AuthorityErrorCode.ORGANIZATION_NOT_FOUND, orgId);
    }

    /**
     * 便捷方法：组织名称已存在异常
     *
     * @param orgName 组织名称
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException organizationNameExists(String orgName) {
        return new AuthorityBusinessException(AuthorityErrorCode.ORGANIZATION_NAME_EXISTS, orgName);
    }

    /**
     * 便捷方法：组织已被禁用异常
     *
     * @param orgId 组织ID
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException organizationDisabled(String orgId) {
        return new AuthorityBusinessException(AuthorityErrorCode.ORGANIZATION_DISABLED, orgId);
    }

    /**
     * 便捷方法：存在子组织，无法删除异常
     *
     * @param orgId 组织ID
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException organizationHasChildren(String orgId) {
        return new AuthorityBusinessException(AuthorityErrorCode.ORGANIZATION_HAS_CHILDREN, orgId);
    }

    // ========== 地区相关便捷方法 ==========

    /**
     * 便捷方法：地区不存在异常
     *
     * @param areaId 地区ID
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException areaNotFound(String areaId) {
        return new AuthorityBusinessException(AuthorityErrorCode.AREA_NOT_FOUND, areaId);
    }

    // ========== 资源相关便捷方法 ==========

    /**
     * 便捷方法：资源不存在异常
     *
     * @param resourceId 资源ID
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException resourceNotFound(String resourceId) {
        return new AuthorityBusinessException(AuthorityErrorCode.RESOURCE_NOT_FOUND, resourceId);
    }

    // ========== 令牌相关便捷方法 ==========

    /**
     * 便捷方法：API令牌不存在异常
     *
     * @param tokenId 令牌ID
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException apiTokenNotFound(Long tokenId) {
        return new AuthorityBusinessException(AuthorityErrorCode.API_TOKEN_NOT_FOUND, tokenId);
    }

    // ========== 客户端相关便捷方法 ==========

    /**
     * 便捷方法：客户端不存在异常
     *
     * @param clientId 客户端ID
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException clientNotFound(String clientId) {
        return new AuthorityBusinessException(AuthorityErrorCode.CLIENT_NOT_FOUND, clientId);
    }

    /**
     * 便捷方法：客户端密钥错误异常
     *
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException clientSecretError() {
        return new AuthorityBusinessException(AuthorityErrorCode.CLIENT_SECRET_ERROR);
    }

    // ========== 认证相关便捷方法 ==========


    /**
     * 便捷方法：无访问权限异常
     *
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException authNoPermission() {
        return new AuthorityBusinessException(AuthorityErrorCode.AUTH_NO_PERMISSION);
    }

    // ========== 系统错误便捷方法 ==========

    /**
     * 便捷方法：系统内部错误异常
     *
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException systemError() {
        return new AuthorityBusinessException(AuthorityErrorCode.SYSTEM_ERROR);
    }

    /**
     * 便捷方法：系统内部错误异常（带自定义消息）
     *
     * @param message 自定义错误消息
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException systemError(String message) {
        return new AuthorityBusinessException(AuthorityErrorCode.SYSTEM_ERROR, message);
    }

    /**
     * 便捷方法：参数无效异常
     *
     * @param message 错误消息
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException invalidParameter(String message) {
        return new AuthorityBusinessException(AuthorityErrorCode.INVALID_PARAMETER, message);
    }

    /**
     * 便捷方法：操作不支持异常
     *
     * @param message 错误消息
     * @return AuthorityBusinessException
     */
    public static AuthorityBusinessException operationNotSupported(String message) {
        return new AuthorityBusinessException(AuthorityErrorCode.OPERATION_NOT_SUPPORTED, message);
    }
}
