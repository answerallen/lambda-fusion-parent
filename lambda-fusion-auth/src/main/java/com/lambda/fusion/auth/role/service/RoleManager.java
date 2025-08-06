package com.lambda.fusion.auth.role.service;

import org.springframework.lang.NonNull;

import java.util.Set;

/**
 * 角色基础操作
 *
 */
public interface RoleManager {


    /**
     * 根据用户查询该用户有哪些角色
     *
     * @param uid
     * @return java.util.Set<java.lang.String>
     */
    @NonNull
    Set<String> getAuthoritiesByUser(String uid);
}
