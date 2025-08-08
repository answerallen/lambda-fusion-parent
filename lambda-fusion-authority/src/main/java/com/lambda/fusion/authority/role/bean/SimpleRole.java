package com.lambda.fusion.authority.role.bean;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户角色")
public class SimpleRole {

    @Schema(description = "角色名")
    private String authority;

    @Schema(description = "别名")
    private String alias;

    public SimpleRole() {}

    public SimpleRole(String authority, String alias) {
        this.authority = authority;
        this.alias = alias;
    }

    public SimpleRole(String authority) {
        this.authority = authority;
    }

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof SimpleRole) {
            return authority.equals(((SimpleRole) obj).authority);
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
