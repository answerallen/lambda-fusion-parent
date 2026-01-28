package com.lambda.fusion.authority;

import com.lambda.fusion.core.annotation.DictMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;

public interface AuthorityConstants {

    String ROLE_MANAGER = "ROLE_MANAGER";

    String OPERATION_LOG_EXECUTOR = "OperationLogExecutor";

    String DEFAULT_GROUP_NAME = "默认分组";

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
            INTERFACE(1, "接口"),
            MENU(2, "菜单"),
            BUTTON(3, "按钮"),
            EXTERNAL_LINK(4, "外部链接"),
            EMBEDDED_PAGE(5, "内嵌页面");

            private final Integer val;
            private final String key;
        }
    }
}
