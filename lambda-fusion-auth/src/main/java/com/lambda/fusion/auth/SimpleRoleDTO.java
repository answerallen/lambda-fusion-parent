package com.lambda.fusion.auth;

import lombok.Getter;

@Getter
public class SimpleRoleDTO {

    /**
     * 角色名
     */
    private String authority;
    /**
     * 别名
     */
    private String alias;

    public SimpleRoleDTO() {}

    public SimpleRoleDTO(String authority, String alias) {
        this.authority = authority;
        this.alias = alias;
    }

    public SimpleRoleDTO(String authority) {
        this.authority = authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof SimpleRoleDTO) {
            return authority.equals(((SimpleRoleDTO) obj).authority);
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
