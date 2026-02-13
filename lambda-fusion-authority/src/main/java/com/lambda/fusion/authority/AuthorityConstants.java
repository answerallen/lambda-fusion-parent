package com.lambda.fusion.authority;

import com.lambda.fusion.core.annotation.DictMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

public interface AuthorityConstants {

    String ROLE_MANAGER = "ROLE_MANAGER";

    String OPERATION_LOG_EXECUTOR = "OperationLogExecutor";

    String DEFAULT_GROUP_NAME = "默认分组";

    List<String> BUILT_IN_ROLES =
            List.of("ROLE_SYSTEM", "ROLE_ADMIN", "ROLE_DEV", "ROLE_USER", "ROLE_MANAGER", "ROLE_ORG");

    String DEFAULT = "default";

    String ADMIN = "admin";

    @SuppressWarnings("unused")
    interface Enums {

        @Getter
        @DictMapper(dictName = "ROLE_TYPE", dictUsage = 0, dictDesc = "角色类型")
        @AllArgsConstructor
        enum RoleType {
            FUNC_ROLE(1, "功能角色"),
            DATA_ROLE(2, "数据角色");

            private final Integer val;
            private final String key;
        }

        @Getter
        @DictMapper(dictName = "MENU_TYPE", dictUsage = 0, dictDesc = "菜单类型")
        @AllArgsConstructor
        enum MenuType {
            MENU(1, "菜单"),
            EMBEDDED_PAGE(2, "内嵌页面"),
            BUTTON(3, "按钮"),
            INTERFACE(4, "接口"),
            EXTERNAL_LINK(5, "外部链接");

            private final Integer val;
            private final String key;
        }
    }
}
