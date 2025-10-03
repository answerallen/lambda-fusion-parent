package com.lambda.fusion.authority.role.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户角色")
public class SimpleRoleVO {

    @Schema(description = "角色名")
    private String authority;

    @Schema(description = "别名")
    private String alias;

    public SimpleRoleVO() {}

    public SimpleRoleVO(String authority, String alias) {
        this.authority = authority;
        this.alias = alias;
    }

    public SimpleRoleVO(String authority) {
        this.authority = authority;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof SimpleRoleVO) {
            return authority.equals(((SimpleRoleVO) obj).authority);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return this.authority.hashCode();
    }

    @Override
    public String toString() {
        return this.authority;
    }
}
