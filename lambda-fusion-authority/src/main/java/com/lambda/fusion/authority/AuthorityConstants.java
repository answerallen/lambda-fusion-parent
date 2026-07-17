package com.lambda.fusion.authority;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.annotation.DictMapper;
import com.lambda.fusion.core.dict.DictEnum;
import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

public interface AuthorityConstants {

    // ==================== 常量相关 ======================
    String DEFAULT = "default";
    String GROUP_ID = "groupId";
    String ALIAS = "alias";
    String TENANT_ID = "tenantId";
    String EXCLUDES = "excludes";

    String DEFAULT_GROUP_NAME = "默认分组";

    List<String> DEFAULT_ROLES = List.of(
            FusionConstants.ROLE_SYSTEM,
            FusionConstants.ROLE_ADMIN,
            FusionConstants.ROLE_DEV,
            FusionConstants.ROLE_USER,
            FusionConstants.ROLE_MANAGER,
            FusionConstants.ROLE_ORG);

    // ==================== 枚举 ======================

    @Getter
    @AllArgsConstructor
    @DictMapper(dictName = "THIRD_TYPE", dictDesc = "三方登录类型")
    enum ThirdType implements DictEnum<String> {
        WX_MA("wxMa", "微信小程序", "wx", "微信用户"),
        ALIPAY_MA("alipayMa", "支付宝小程序", "alipay", "支付宝用户"),
        DING_TALK("dingTalk", "钉钉", "dingTalk", "钉钉用户"),
        WX_OPEN("wxOpen", "微信开放平台", "wxOpen", "微信用户");

        @EnumValue
        @JsonValue
        private final String code;

        private final String label;
        private final String usernamePrefix;
        private final String defaultNickname;

        public static ThirdType of(String code) {
            for (ThirdType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unsupported third-party login type: " + code);
        }
    }

    @Getter
    @DictMapper(dictName = "ROLE_TYPE", dictUsage = 0, dictDesc = "角色类型")
    @AllArgsConstructor
    enum RoleType implements DictEnum<Integer> {
        FUNC_ROLE(0, "功能角色"),
        DATA_ROLE(1, "数据角色");

        @EnumValue
        @JsonValue
        private final Integer code;

        private final String label;
    }

    @Getter
    @DictMapper(dictName = "MENU_TYPE", dictUsage = 0, dictDesc = "菜单类型")
    @AllArgsConstructor
    enum MenuType implements DictEnum<Integer> {
        MENU(1, "菜单"),
        EMBEDDED_PAGE(2, "内嵌页面"),
        BUTTON(3, "按钮"),
        INTERFACE(4, "接口"),
        EXTERNAL_LINK(5, "外部链接");

        @EnumValue
        @JsonValue
        private final Integer code;

        private final String label;

        public static MenuType of(int i) {
            MenuType[] values = MenuType.values();
            return Arrays.stream(values)
                    .filter(menuType -> menuType.code == i)
                    .findFirst()
                    .orElse(null);
        }

        public Boolean isButton() {
            return BUTTON.equals(this);
        }
    }
}
