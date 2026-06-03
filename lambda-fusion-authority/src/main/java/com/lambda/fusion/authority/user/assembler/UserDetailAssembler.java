package com.lambda.fusion.authority.user.assembler;

import cn.hutool.core.util.ObjectUtil;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.cloud.sse.SseEmitterManager;
import com.lambda.fusion.authority.organization.model.OrganizationEntity;
import com.lambda.fusion.authority.organization.model.SimpleOrganization;
import com.lambda.fusion.authority.organization.service.OrganizationService;
import com.lambda.fusion.authority.role.model.SimpleRole;
import com.lambda.fusion.authority.user.mapper.UserFieldsMapper;
import com.lambda.fusion.authority.user.mapper.UserInfoMapper;
import com.lambda.fusion.authority.user.mapper.UserMapper;
import com.lambda.fusion.authority.user.model.PopulateUserInfo;
import com.lambda.fusion.authority.user.model.User;
import com.lambda.fusion.authority.user.model.entity.UserFieldsEntity;
import com.lambda.fusion.authority.user.model.UserInfo;
import com.lambda.fusion.authority.user.model.entity.UserInfoEntity;
import com.lambda.fusion.authority.user.service.UserOnlineLogService;
import com.lambda.fusion.authority.utils.AuthorityHelper;
import com.lambda.fusion.authority.utils.UserInfoConverter;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.security.web.form.FormLockingStrategy;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@SuppressFBWarnings("EI_EXPOSE_REP2")
@Component
@RequiredArgsConstructor
public class UserDetailAssembler {
    private final UserMapper userMapper;
    private final UserInfoMapper userInfoMapper;
    private final UserFieldsMapper userFieldsMapper;
    private final OrganizationService organizationService;
    private final SseEmitterManager sseEmitterManager;
    private final UserOnlineLogService userOnlineLogService;
    private final FormLockingStrategy formLockingStrategy;

    public List<User> populateUserDetails(List<User> users, String tenantId) {
        List<User> records = userMapper.selectUsers(users);
        PopulateUserInfo populateUserInfo = extractUserBatchInfo(records);

        Map<String, String> orgNames = buildOrgFullNameMap(populateUserInfo.getOrgIds());
        Map<String, Map<String, Object>> personInfoMap = buildUserPersonFieldMap(populateUserInfo.getUsernames());
        Map<String, UserInfoEntity> userInfoMap = buildUserInfoMap(populateUserInfo);

        for (User user : records) {
            assembleUserInfo(user, orgNames, personInfoMap, tenantId, userInfoMap);
        }
        return records;
    }

    public boolean isOnline(String username) {
        if (sseEmitterManager.getActiveClients().contains(username)) {
            return true;
        }
        if (Boolean.TRUE.equals(userOnlineLogService.isOnline(username, null))) {
            userOnlineLogService.offline(username, null);
        }
        return false;
    }

    public Map<String, Map<String, Object>> buildUserPersonFieldMap(Set<String> usernames) {
        List<UserFieldsEntity> fields = userFieldsMapper.getPersonUser(usernames);
        return UserInfoConverter.buildUserFieldsMap(fields);
    }

    private Map<String, UserInfoEntity> buildUserInfoMap(PopulateUserInfo populateUserInfo) {
        List<UserInfoEntity> userInfos = userInfoMapper.selectByIds(populateUserInfo.getUsernames());
        return userInfos.stream()
                .collect(Collectors.toMap(UserInfoEntity::getUsername, Function.identity(), (a, b) -> a));
    }

    private void assembleUserInfo(
            User user,
            Map<String, String> orgNames,
            Map<String, Map<String, Object>> personInfo,
            String tenantId,
            Map<String, UserInfoEntity> userInfoMap) {
        assembleUserOrgInfo(orgNames, user);
        assembleUserPersonal(personInfo, user);
        assembleUserLockState(user);
        assembleUserPermissionInfo(user, tenantId);
        user.setOnline(isOnline(user.getUsername()));

        UserInfoEntity userInfoEntity = userInfoMap.get(user.getUsername());
        if (userInfoEntity != null) {
            UserInfo userInfo = ConvertUtils.convert(userInfoEntity);
            user.setProps(userInfo);
        }
        if (CollectionUtils.isNotEmpty(user.getAuthorities())) {
            user.getAuthorities().sort(Comparator.comparing(SimpleRole::getAuthority));
        }
    }

    private PopulateUserInfo extractUserBatchInfo(List<User> users) {
        Set<String> usernames = Sets.newHashSet();
        Set<String> orgIds = Sets.newHashSet();
        for (User item : users) {
            usernames.add(item.getUsername());
            if (hasOrganization(item)) {
                orgIds.add(item.getOrganization().getId());
            }
        }
        PopulateUserInfo populateUserInfo = new PopulateUserInfo();
        populateUserInfo.setUsernames(usernames);
        populateUserInfo.setOrgIds(orgIds);
        return populateUserInfo;
    }

    private void assembleUserPermissionInfo(User user, String tenantId) {
        if (AuthorityHelper.isTenant(user)) {
            user.setDisableAssignment(true);
        }
        if (StringUtils.isNotBlank(user.getTenantId()) && !Objects.equals(tenantId, user.getTenantId())) {
            user.setDisableOperations(true);
        }
    }

    private void assembleUserLockState(User user) {
        user.setLocked(formLockingStrategy.getLockedState(user.getUsername()));
    }

    private void assembleUserPersonal(Map<String, Map<String, Object>> allPersonUserMap, User user) {
        if (allPersonUserMap.containsKey(user.getUsername())) {
            user.setPersonal(allPersonUserMap.get(user.getUsername()));
        }
    }

    private void assembleUserOrgInfo(Map<String, String> orgNames, User user) {
        if (hasOrganization(user)) {
            SimpleOrganization org = user.getOrganization();
            org.setFullName(orgNames.getOrDefault(org.getId(), org.getAlias()));
        }
    }

    private boolean hasOrganization(@NonNull User user) {
        SimpleOrganization org = user.getOrganization();
        return org != null && StringUtils.isNotBlank(org.getId());
    }

    private Map<String, String> buildOrgFullNameMap(Set<String> orgIds) {
        if (CollectionUtils.isEmpty(orgIds)) {
            return Collections.emptyMap();
        }
        List<OrganizationEntity> organizations = organizationService.listByIds(orgIds);
        if (CollectionUtils.isEmpty(organizations)) {
            return Collections.emptyMap();
        }
        Map<String, String> orgNameMap = Maps.newHashMap();
        Map<String, String> orgParentKeyMap = Maps.newHashMap();
        Set<String> parentOrgIds = Sets.newHashSet();
        for (OrganizationEntity item : organizations) {
            String parentKeys = item.getParentKeys();
            orgParentKeyMap.put(item.getId(), item.getAlias());
            orgNameMap.put(item.getId(), parentKeys);
            if (StringUtils.isNotBlank(parentKeys)) {
                Collections.addAll(parentOrgIds, parentKeys.split(FusionConstants.TREE_SPLIT));
            }
        }
        Map<String, String> result = Maps.newHashMap();
        if (CollectionUtils.isEmpty(parentOrgIds)) {
            return result;
        }
        List<OrganizationEntity> parents = organizationService.listByIds(parentOrgIds);
        Map<String, String> parentNameMap =
                parents.stream().collect(Collectors.toMap(OrganizationEntity::getId, OrganizationEntity::getName));
        orgNameMap.forEach((key, value) -> {
            StringBuilder builder = new StringBuilder();
            if (StringUtils.isNotBlank(value)) {
                for (String token : value.split(FusionConstants.TREE_SPLIT)) {
                    String parentName = parentNameMap.get(token);
                    if (ObjectUtil.isNotEmpty(parentName)) {
                        builder.append(parentName).append(FusionConstants.TREE_SPLIT);
                    }
                }
            }
            builder.append(orgParentKeyMap.get(key));
            result.put(key, builder.toString());
        });
        return result;
    }
}
