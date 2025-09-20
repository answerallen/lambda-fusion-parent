package com.lambda.fusion.authority.user.optimizer;

import com.lambda.fusion.authority.user.model.MutableUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 用户查询优化工具类
 * 
 * <p>提供用户相关的批量查询优化方法，减少N+1查询问题，提升性能。
 * 
 * @author Lambda Fusion Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserQueryOptimizer {

    /**
     * 批量获取用户组织信息映射
     * 
     * @param orgIds 组织ID集合
     * @return 组织ID到组织名称的映射
     */
    public Map<String, String> batchGetOrgNames(Set<String> orgIds) {
        if (CollectionUtils.isEmpty(orgIds)) {
            return Collections.emptyMap();
        }
        
        // TODO: 实现批量查询组织名称的逻辑
        // 这里应该调用OrganizationMapper的批量查询方法
        return new HashMap<>();
    }

    /**
     * 批量获取用户个人信息映射
     * 
     * @param userIds 用户ID集合
     * @return 用户ID到个人信息的映射
     */
    public Map<String, Map<String, String>> batchGetPersonInfo(Set<String> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        
        // TODO: 实现批量查询用户个人信息的逻辑
        return new HashMap<>();
    }

    /**
     * 批量获取用户在线状态
     * 
     * @param userIds 用户ID集合
     * @return 在线用户ID集合
     */
    public Set<String> batchGetOnlineUsers(Set<String> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptySet();
        }
        
        // TODO: 实现批量查询用户在线状态的逻辑
        return new HashSet<>();
    }

    /**
     * 从用户列表中提取所有相关的ID信息
     * 
     * @param users 用户列表
     * @return 包含用户ID、组织ID等信息的参数对象
     */
    public UserBatchQueryParams extractBatchQueryParams(List<MutableUser> users) {
        if (CollectionUtils.isEmpty(users)) {
            return new UserBatchQueryParams();
        }

        Set<String> userIds = new HashSet<>();
        Set<String> orgIds = new HashSet<>();

        for (MutableUser user : users) {
            if (user.id() != null) {
                userIds.add(user.id());
            }
            if (user.getOrg() != null) {
                orgIds.add(user.getOrg().getId());
            }
        }

        return new UserBatchQueryParams(userIds, orgIds);
    }

    /**
     * 批量查询参数封装类
     */
    public static class UserBatchQueryParams {
        private final Set<String> userIds;
        private final Set<String> orgIds;

        public UserBatchQueryParams() {
            this.userIds = new HashSet<>();
            this.orgIds = new HashSet<>();
        }

        public UserBatchQueryParams(Set<String> userIds, Set<String> orgIds) {
            this.userIds = userIds != null ? userIds : new HashSet<>();
            this.orgIds = orgIds != null ? orgIds : new HashSet<>();
        }

        public Set<String> getUserIds() {
            return userIds;
        }

        public Set<String> getOrgIds() {
            return orgIds;
        }
    }
}