package com.lambda.fusion.autoconfig;

public final class AuthorizeConstants {

    private AuthorizeConstants() {}

    public static final String USER_NOT_FOUND = "lambda.authority.user.notfound";
    public static final String USER_NAME_NOT_EMPTY = "lambda.authority.user.username.notempty";
    public static final String USER_NAME_EXIST = "lambda.authority.user.username.existed";
    public static final String ROLE_NOT_FOUND = "lambda.authority.role.notfound";
    public static final String ROLE_NAME_NOT_EMPTY = "lambda.authority.role.name.notempty";
    public static final String ROLE_SELF_REFUSED = "lambda.authority.role.authorize.self.refused";
    public static final String ROLE_GROUP_ILLEGAL_OPERATION_DEL_DEFAULT =
            "lambda.authority.role.group.illegal.operation.del.default";
    public static final String RES_ID_NOT_NULL = "lambda.authority.resource.id.notnull";
    public static final String RES_LOCATION_ERROR = "lambda.authority.resource.button.location.incorrect";
    public static final String ORG_NOT_EMPTY = "lambda.authority.organ.notempty";
    public static final String ORG_NOT_FOUND = "lambda.authority.organ.notfound";
    public static final String ORG_ID_NOT_EMPTY = "lambda.authority.organ.id.notempty";
    public static final String ORG_USER_NOT_EMPTY = "lambda.authority.organ.user.notempty";
    public static final String CLIENT_NOT_FOUND = "lambda.authority.client.notfound";
    public static final String TENANT_NOT_FOUND = "lambda.authority.tenant.notfound";
    public static final String TENANT_ID_NOT_EMPTY = "lambda.authority.tenant.id.notempty";
    public static final String TENANT_NO_AUTHORITY = "lambda.authority.no.tenant.authority";
    public static final String USER_MOBILE_EXIST = "lambda.authority.user.mobile.exist";

    /**
     * 123456 MD5两次后的结果值
     */
    public static final String PASSWORD = "123456";

    public static final String ROLE_MANAGER = "ROLE_MANAGER";

    public static final String CACHE_MANAGER = "AuthorityCacheManager";

    public static final String LA_OPERATION_LOG_EXECUTOR = "OperationLogExecutor";

    public static final String DEFAULT_GROUP_NAME = "默认分组";

    public static final String MANAGED = "1";

}
