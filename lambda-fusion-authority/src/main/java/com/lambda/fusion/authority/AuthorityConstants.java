package com.lambda.fusion.authority;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lambda.fusion.core.annotation.DictMapper;
import java.util.List;

import com.lambda.fusion.core.enums.DictEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

public interface AuthorityConstants {

    String ROLE_MANAGER = "ROLE_MANAGER";

    String OPERATION_LOG_EXECUTOR = "OperationLogExecutor";

    String DEFAULT_GROUP_NAME = "默认分组";

    List<String> BUILT_IN_ROLES =
            List.of("ROLE_SYSTEM", "ROLE_ADMIN", "ROLE_DEV", "ROLE_USER", "ROLE_MANAGER", "ROLE_ORG");

    String DEFAULT = "default";

    String ADMIN = "admin";
    String DEV = "dev";
    String USERNAME = "username";


    //==================== 枚举 ======================

    @Getter
    @DictMapper(dictName = "ROLE_TYPE", dictUsage = 0, dictDesc = "角色类型")
    @AllArgsConstructor
    enum RoleType implements DictEnum<Integer> {
        FUNC_ROLE(1, "功能角色"),
        DATA_ROLE(2, "数据角色");

        @EnumValue
        @JsonValue
        private final Integer code;
        private final String label;
    }

    @Getter
    @DictMapper(dictName = "MENU_TYPE", dictUsage = 0, dictDesc = "菜单类型")
    @AllArgsConstructor
    enum MenuType implements DictEnum<Integer>{
        MENU(1, "菜单"),
        EMBEDDED_PAGE(2, "内嵌页面"),
        BUTTON(3, "按钮"),
        INTERFACE(4, "接口"),
        EXTERNAL_LINK(5, "外部链接");

        @EnumValue
        @JsonValue
        private final Integer code;
        private final String label;
    }
}
