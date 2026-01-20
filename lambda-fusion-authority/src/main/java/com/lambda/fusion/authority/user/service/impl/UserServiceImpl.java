package com.lambda.fusion.authority.user.service.impl;

import static com.lambda.fusion.authority.AuthorityConstants.CACHE_MANAGER;
import static com.lambda.fusion.authority.AuthorityConstants.MANAGED;
import static com.lambda.fusion.core.Constants.ROLE_DEV;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.cloud.sse.SseEmitterManager;
import com.lambda.fusion.authority.AuthorityConstants;
import com.lambda.fusion.authority.AuthorityProperties;
import com.lambda.fusion.authority.organization.domain.OrganizationEntity;
import com.lambda.fusion.authority.organization.domain.OrganizationSummary;
import com.lambda.fusion.authority.organization.domain.UserOrganizationEntity;
import com.lambda.fusion.authority.organization.mapper.OrganizationMapper;
import com.lambda.fusion.authority.organization.mapper.UserOrganizationMapper;
import com.lambda.fusion.authority.organization.service.OrganizationService;
import com.lambda.fusion.authority.role.mapper.RoleMapper;
import com.lambda.fusion.authority.role.model.SimpleRole;
import com.lambda.fusion.authority.user.mapper.*;
import com.lambda.fusion.authority.user.model.*;
import com.lambda.fusion.authority.user.service.UserService;
import com.lambda.fusion.core.Constants;
import com.lambda.fusion.core.identity.UserPrincipal;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthorityProperties properties;
    private final UserInfoMapper userInfoMapper;
    private final UserFieldsMapper userFieldsMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final UserPasswordMapper userUpdatePwdLogMapper;
    private final OrganizationService organizationService;
    private final OrganizationMapper organizationMapper;
    private final SseEmitterManager sseEmitterManager;

    /***
     * @param username 用户账号
     * @return {@link boolean}
     **/
    @Override
    public boolean checkUserName(String username) {
        return userMapper.hasExists(username);
    }

    @Override
    public List<String> getUserNamesByAuthority(String orgid, @NotBlank String authority) {
        if (!authority.equals(AuthorityConstants.ROLE_MANAGER)) {
            orgid = null;
        }
        return userMapper.selectUsernamesByAuthority(orgid, authority);
    }

    @Override
    public List<String> getUserNamesByOrgId(String orgid, Integer type) {
        return userMapper.selectUsernameByOrgId(orgid, type);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<User> getAllUsers() {
        return userMapper.selectAllUsers();
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public User getUserByUsername(String username) {
        Assert.notNull(username, "username is not empty");
        User user = userMapper.selectUserByUsername(username);
        if (user != null) {
            user.setOnline(sseEmitterManager.getActiveClients().contains(username));
            user.setLocked(false);
            UserInfoEntity userInfoEntity = userInfoMapper.getProps(username);
            if (userInfoEntity != null) {
                UserInfo userInfo = ConvertUtils.convert(userInfoEntity);
                user.setProps(userInfo);
            }
            List<UserFieldsEntity> fields = userFieldsMapper.getListByUsername(username);
            Map<String, Map<String, String>> allPersonUserMap;
            if (CollectionUtils.isNotEmpty(fields)) {
                allPersonUserMap = this.convertPersonMap(fields);
                user.setPersonal(allPersonUserMap.get(username));
            }
        }
        return user;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public User getCurrentUser(UserPrincipal userPrincipal) {
        User user = this.getUserByUsername(userPrincipal.getName());
        UserInfo props = user.getProps();
        if (props != null && properties.getPasswordStrategy().getEnablePeriodChange()) {
            boolean notMatched = !userPrincipal.isDev()
                    && !userPrincipal.isAdmin()
                    && !userPrincipal.isManager()
                    && !userPrincipal.isTenantManager();
            // 判断密码是否需要更新
            if (notMatched && ObjectUtil.equals(props.getPasswordResetRequired(), false)) {
                List<UserPasswordEntity> userUpdatePwdLogEntities =
                        userUpdatePwdLogMapper.selectList(new LambdaQueryWrapper<UserPasswordEntity>()
                                .select(UserPasswordEntity::getUpdateTime)
                                .eq(UserPasswordEntity::getUsername, userPrincipal.getName())
                                .isNotNull(UserPasswordEntity::getUpdateTime)
                                .orderByDesc(UserPasswordEntity::getUpdateTime));
                if (CollectionUtils.isNotEmpty(userUpdatePwdLogEntities)) {
                    UserPasswordEntity userUpdatePwdLogEntity = userUpdatePwdLogEntities.getFirst();
                    Integer passwordModifyDays =
                            properties.getPasswordStrategy().getPeriodChangeDays();
                    LocalDateTime nowTime = LocalDateTime.now();
                    LocalDateTime lastUpdateTime = DateUtil.toLocalDateTime(userUpdatePwdLogEntity.getUpdateTime());
                    long days = ChronoUnit.DAYS.between(lastUpdateTime, nowTime);
                    if (days >= passwordModifyDays) {
                        props.setPasswordResetRequired(true);
                        props.setPasswordModifyDays((int) days);
                        user.setProps(props);
                    }
                }
            }
        }
        return user;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<User> getUsers(List<User> users) {
        if (CollectionUtils.isEmpty(users)) {
            return users;
        }
        return userMapper.selectUsers(users);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Page<User> getUsers(Page<User> pagination, UserSearchParams parameters) {
        String tenantId = parameters.getTenantId();

        // 执行分页查询
        pagination = userMapper.selectUserPage(pagination, parameters);
        List<User> users = pagination.getRecords();

        if (CollectionUtils.isNotEmpty(users)) {
            // 补充用户详细信息
            List<User> enrichedUsers = enrichUserDetails(users, tenantId);
            pagination.setRecords(enrichedUsers);
        }

        return pagination;
    }

    /**
     * 丰富用户详细信息
     */
    private List<User> enrichUserDetails(List<User> users, String tenantId) {
        List<User> records = userMapper.selectUsers(users);
        UserTempParameters tempParams = getUserTempParameters(records);

        // 批量获取关联数据
        Map<String, String> orgNames = getOrgFullNamesByOrgIds(tempParams.getOrgIds());
        Map<String, Map<String, String>> personInfo = getPersonsByUids(tempParams.getUids());

        // 补充用户信息
        for (User user : records) {
            fillSingleUserInfo(user, orgNames, personInfo, tenantId);
        }

        return records;
    }

    /**
     * 补充单个用户的详细信息
     */
    private void fillSingleUserInfo(
            User user, Map<String, String> orgNames, Map<String, Map<String, String>> personInfo, String tenantId) {
        supplementUserOrgInfo(orgNames, user);
        supplementUserPersonInfo(personInfo, user);
        supplementUserLockState(user);
        supplementUserPermissionInfo(user, tenantId);
        boolean online = sseEmitterManager.getActiveClients().contains(user.getUsername());
        user.setOnline(online);
        // 角色排序
        if (CollectionUtils.isNotEmpty(user.getAuthorities())) {
            user.getAuthorities().sort(Comparator.comparing(SimpleRole::getAuthority));
        }
    }

    private Map<String, Map<String, String>> getPersonsByUids(Set<String> uids) {
        List<UserFieldsEntity> fields = userFieldsMapper.getPersonUser(uids);
        return this.convertPersonMap(fields);
    }

    /**
     * 整理用户临时参数
     */
    private UserTempParameters getUserTempParameters(List<User> users) {
        Set<String> uids = Sets.newHashSet();
        Set<String> orgIds = Sets.newHashSet();
        for (User item : users) {
            uids.add(item.getUsername());
            if (isBindOrg(item)) {
                orgIds.add(item.getOrganizationSummary().getId());
            }
        }
        UserTempParameters parameters = new UserTempParameters();
        parameters.setUids(uids);
        parameters.setOrgIds(orgIds);
        return parameters;
    }

    /**
     * 补充完善用户权限信息
     */
    private void supplementUserPermissionInfo(User item, String tenantId) {
        // todo if (RoleUtil.isTenant(item)) {
        // item.setDisAllocation(true);
        // }
        extractedPermission(tenantId, item);
    }

    /**
     * 补充完善用户锁定信息
     */
    private void supplementUserLockState(User item) {
        // 锁定状态
    }

    private void supplementUserPersonInfo(Map<String, Map<String, String>> allPersonUserMap, User item) {
        if (allPersonUserMap.containsKey(item.getUsername())) {
            item.setPersonal(allPersonUserMap.get(item.getUsername()));
        }
    }

    /**
     * 补充完善用户组织信息
     */
    private void supplementUserOrgInfo(Map<String, String> orgnames, User item) {
        if (isBindOrg(item)) {
            OrganizationSummary org = item.getOrganizationSummary();
            org.setFullName(orgnames.getOrDefault(org.getId(), org.getAlias()));
        }
    }

    /**
     * 是否绑定了组织机构
     */
    private boolean isBindOrg(@NonNull User user0) {
        OrganizationSummary org = user0.getOrganizationSummary();
        return org != null && StringUtils.isNotBlank(org.getId());
    }

    /**
     * 获取组织全名
     */
    private Map<String, String> getOrgFullNamesByOrgIds(Set<String> orgIds) {
        if (CollectionUtils.isEmpty(orgIds)) {
            return Collections.emptyMap();
        }
        List<OrganizationEntity> organizations = organizationMapper.selectByIds(orgIds);
        if (CollectionUtils.isEmpty(organizations)) {
            return Collections.emptyMap();
        }
        Map<String, String> newed = Maps.newHashMap();
        Map<String, String> names0 = Maps.newHashMap();
        Set<String> parentKeys = Sets.newHashSet();
        for (OrganizationEntity item : organizations) {
            String parentKeys1 = item.getParentKeys();
            names0.put(item.getId(), item.getAlias());
            newed.put(item.getId(), parentKeys1);
            if (StringUtils.isNotBlank(parentKeys1)) {
                Collections.addAll(parentKeys, parentKeys1.split(Constants.TREE_SPLIT));
            }
        }
        Map<String, String> result = Maps.newHashMap();
        if (CollectionUtils.isEmpty(parentKeys)) {
            return result;
        }
        List<OrganizationEntity> parents = organizationMapper.selectByIds(parentKeys);
        Map<String, String> collected =
                parents.stream().collect(Collectors.toMap(OrganizationEntity::getId, OrganizationEntity::getName));
        newed.forEach((key, value) -> {
            StringBuilder builder = new StringBuilder();
            if (StringUtils.isNotBlank(value)) {
                for (String token : value.split(Constants.TREE_SPLIT)) {
                    builder.append(collected.get(token)).append(Constants.TREE_SPLIT);
                }
            }
            builder.append(names0.get(key));
            result.put(key, builder.toString());
        });
        return result;
    }

    /**
     * 构造 用户扩展信息对象
     *
     * @param fields 所有的数据
     * @return Map<String, Map < String, String>>
     */
    private Map<String, Map<String, String>> convertPersonMap(List<UserFieldsEntity> fields) {
        Map<String, Map<String, String>> maps = Maps.newHashMap();
        fields.forEach(userFieldsDO -> {
            String username = userFieldsDO.getUsername();
            Map<String, String> map;
            if (!maps.containsKey(username)) {
                map = Maps.newHashMap();
            } else {
                map = maps.get(username);
            }
            map.put(userFieldsDO.getFieldName(), userFieldsDO.getFieldValue());
            maps.put(username, map);
        });
        return maps;
    }

    /**
     * 用户扩展信息map 转 用户新增字段信息map
     *
     * @param personal 用户新增字段信息map
     * @param username 用户名
     * @return List<UserFields>
     */
    private List<UserFieldsEntity> convertPersonBean(Map<String, String> personal, String username) {
        List<UserFieldsEntity> userFieldDOS = new ArrayList<>(personal.size());
        personal.forEach((k, v) -> {
            UserFieldsEntity info = new UserFieldsEntity();
            info.setUsername(username);
            info.setFieldName(k);
            info.setFieldValue(v);
            userFieldDOS.add(info);
        });
        return userFieldDOS;
    }

    /***
     * 验证是否有权限操作
     *
     * @param tenantId 租户id
     * @param item     当前用户
     */
    private void extractedPermission(String tenantId, User item) {
        if (StringUtils.isNotBlank(item.getTenantId()) && !Objects.equals(tenantId, item.getTenantId())) {
            item.setNoPermission(true);
        }
    }

    @Override
    public List<String> getUidsByOrg(String forge, String role) {
        return userMapper.selectUsernamesByOrg(forge, role);
    }

    @Override
    public Set<String> getPermissions(LoginUser operator, String source) {
        return userMapper.selectUserPermissions(source);
    }

    @Override
    public void batchSavePermissions(LoginUser operator, String target, Set<String> permissions) {
        List<UserRoleEntity> userRoleEntities = permissions.stream()
                .map(permission -> {
                    UserRoleEntity userRoleEntity = new UserRoleEntity();
                    userRoleEntity.setUsername(target);
                    userRoleEntity.setTenantId(operator.getTenantId());
                    userRoleEntity.setAuthority(permission);
                    return userRoleEntity;
                })
                .collect(Collectors.toList());
        userRoleMapper.insert(userRoleEntities);
    }

    @Override
    public void batchSavePermissions(LoginUser operator, String source, String target, Set<String> permissions) {
        if (CollectionUtils.isEmpty(permissions)) {
            return;
        }
        List<RoleResources> insertResources = userMapper.selectRoleResources(source, null, permissions);
        String tenantId = operator.getTenantId();
        // TODO 批量保存权限性能优化
        for (RoleResources roleResources : insertResources) {
            userMapper.saveUserPermission(target, roleResources, tenantId);
        }
    }

    @Override
    public void batchUpdatePermissions(LoginUser operator, String source, String target, Set<String> permissions) {
        List<RoleResources> updateResources = userMapper.selectRoleResources(source, MANAGED, permissions);
        if (CollectionUtils.isNotEmpty(updateResources)) {
            userMapper.batchUpdateUserPermissions(target, MANAGED, updateResources, operator.getTenantId());
        }
    }

    @Override
    public List<Permission> getUserPermissions(String username, String mode) {
        Assert.notNull(username, "username not empty");
        User user = userMapper.selectUserByUsername(username);
        Assert.notNull(user, "user not found");
        List<String> ids = Lists.newArrayList(username);
        List<SimpleRole> authorities = user.getAuthorities();
        if (CollectionUtils.isNotEmpty(authorities)) {
            boolean admin = authorities.stream().anyMatch(e -> ROLE_DEV.equals(e.getAuthority()));
            if (!admin) {
                for (SimpleRole role : authorities) {
                    ids.add(role.getAuthority());
                }
            } else {
                ids = Collections.emptyList();
            }
        }
        return userMapper.selectUserPermissionsByIdsAndMode(ids, mode);
    }

    @CacheEvict(value = "LAResourceOwners", allEntries = true, cacheManager = CACHE_MANAGER)
    @Override
    public String addUser(CreateUser createUser, LoginUser operator) {
        UserEntity userEntity = createUser.toEntity();
        Assert.notNull(userEntity, "user is not null");

        boolean hasExists = userMapper.hasExists(userEntity.getUsername());
        Assert.isTrue(hasExists, "该用户名已被使用");

        AuthorityProperties.PasswordStrategy strategy = properties.getPasswordStrategy();
        String originPassword = userEntity.getPassword();
        Password encodePassword = obtainPassword(strategy, originPassword);
        userEntity.setPassword(passwordEncoder.encode(encodePassword.getEncrypted()));

        userEntity.setCreateDate(new Date());
        userEntity.setTenantId(operator.getTenantId());
        userEntity.setCreator(operator.getName());
        userMapper.insert(userEntity);

        List<SimpleRole> roles = createUser.getAuthorities();
        addUserRoles(operator, roles, userEntity);

        if (Objects.isNull(createUser.getProps())) {
            createUser.setProps(new UserInfo());
        }

        UserInfoEntity userInfoEntity = createUser.getProps().toEntity();
        userInfoEntity.setUsername(userEntity.getUsername());
        userInfoMapper.insert(userInfoEntity);

        OrganizationSummary org = createUser.getOrg();
        if (org != null && StringUtils.isNotBlank(org.getId())) {
            userOrganizationMapper.insert(
                    new UserOrganizationEntity(userEntity.getUsername(), org.getId(), operator.getTenantId()));
        }
        return encodePassword.getOrigin();
    }

    private void addUserRoles(LoginUser operator, List<SimpleRole> roles, UserEntity userEntity) {
        if (CollectionUtils.isNotEmpty(roles)) {
            List<UserRoleEntity> userRoleEntities = roles.stream()
                    .map(SimpleRole::getAuthority)
                    .filter(StrUtil::isNotEmpty)
                    .map(authority -> {
                        Assert.notNull(authority, "role not found");
                        if (authority.startsWith(Constants.ROLE_TENANT)) {
                            authority = Constants.ROLE_TENANT;
                        }
                        UserRoleEntity userRoleEntity = new UserRoleEntity();
                        userRoleEntity.setAuthority(authority);
                        userRoleEntity.setTenantId(operator.getTenantId());
                        userRoleEntity.setUsername(userEntity.getUsername());
                        return userRoleEntity;
                    })
                    .collect(Collectors.toList());
            userRoleMapper.insert(userRoleEntities);
        }
    }

    @CacheEvict(value = "LAResourceOwners", allEntries = true, cacheManager = CACHE_MANAGER)
    @Override
    public void updateUser(UpdateUser updateUser, LoginUser operator) {
        UserEntity userEntity = updateUser.toEntity();
        int updated = userMapper.updateById(userEntity);
        Assert.isTrue(updated == 0, "用户更新失败！");
        userRoleMapper.deleteUserRoles(userEntity.getUsername());
        addUserRoles(operator, updateUser.getAuthorities(), userEntity);
        if (MapUtils.isNotEmpty(updateUser.getPersonal())) {
            List<UserFieldsEntity> fields = this.convertPersonBean(updateUser.getPersonal(), userEntity.getUsername());
            this.userFieldsMapper.deleteByUsername(userEntity.getUsername());
            this.userFieldsMapper.insert(fields);
        }
    }

    /**
     * <ol>
     * <li>当结果值不为空时，用户的组织要变更</li>
     * <li>当结果值为空时，用户的组织无需变更</li>
     * </ol>
     *
     * @param source 页面值
     * @param target 实际值
     */
    @Nullable
    private OrganizationSummary orgUpdated(OrganizationSummary source, OrganizationSummary target) {
        if (source == null) {
            return new OrganizationSummary();
        }
        if (target == null) {
            return source;
        }
        String id0 = source.getId();
        String id1 = target.getId();
        if (StringUtils.isBlank(id0)) {
            return new OrganizationSummary();
        } else if (id0.equals(id1)) {
            return null;
        } else {
            return source;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(LoginUser operator, String username) {
        Assert.notNull(username, "username not empty");
        Assert.isTrue(!username.equals(operator.getName()), "lambda.authority.no.operation.authority");
        boolean exists = userMapper.hasExists(username);
        if (exists) {
            userMapper.deleteUser(username);
            userRoleMapper.deleteUserRoles(username);
            roleMapper.deleteResourceRoleByAuthority(username);
            userOrganizationMapper.deleteUserOrganizationByUser(username);
            userFieldsMapper.deleteByUsername(username);
            // todo 删除用户数据权限
        }
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<User> getUsersByKey(String key) {
        Assert.notNull(key, "key not empty");
        return userMapper.selectUsersByKey(key);
    }

    @Override
    public void updateUserPassword(String username, String oldpassword, String newpassword) {
        UserEntity userEntity = userMapper.selectById(username);
        Assert.notNull(userEntity, "user not found");
        boolean isChecked = passwordEncoder.matches(oldpassword, userEntity.getPassword());
        Assert.isTrue(isChecked, "lambda.authority.user.password.incorrect");
        String encoded = passwordEncoder.encode(newpassword);
        UserPasswordEntity userUpdatePwdLogEntity = new UserPasswordEntity();
        userUpdatePwdLogEntity.setPassword(encoded);
        userUpdatePwdLogEntity.setUsername(username);
        userUpdatePwdLogMapper.insertLog(userUpdatePwdLogEntity);
        userMapper.updatePassword(username, encoded);
        userInfoMapper.updateStatus(username, false);
        // todo 初始化令牌
    }

    @Override
    public String resetUserPassword(ResetPassword resetPassword) {
        AuthorityProperties.PasswordStrategy strategy = properties.getPasswordStrategy();
        Password password = obtainPassword(strategy, resetPassword.getNewPassword());
        userMapper.updatePassword(resetPassword.getUsername(), passwordEncoder.encode(password.getEncrypted()));
        UserInfoEntity userInfoEntity = new UserInfoEntity();
        userInfoEntity.setUsername(resetPassword.getUsername());
        userInfoEntity.setUpdatePwd(true);
        int count = userInfoMapper.updateStatus(resetPassword.getUsername(), true);
        if (0 == count) {
            userInfoMapper.insert(userInfoEntity);
        }
        // todo 初始化令牌
        return password.getOrigin();
    }

    @Override
    public void prohibitUser(LoginUser operator, Integer type, String username) {
        Assert.notNull(username, "username not null");
        User user = userMapper.selectUserByUsername(username);
        Assert.notNull(user, "user not found");
        Assert.isTrue(!operator.getName().equals(username), "lambda.authority.no.operation.authority");
        userMapper.prohibitUser(type, username);
    }

    @Override
    public void unlockUser(String username, LoginUser operator) {}

    public static String md5f2(String password) {
        return DigestUtils.md5Hex(DigestUtils.md5Hex(password));
    }

    @Getter
    @Setter
    @AllArgsConstructor
    private static class Password {
        String origin;
        String encrypted;
    }

    /**
     * 根据密码策略生成密码
     *
     * @param strategy  密码策略
     * @param parameter 前台参数
     * @return 密码对象
     */
    static Password obtainPassword(AuthorityProperties.PasswordStrategy strategy, String parameter) {
        String origin;
        String password;
        AuthorityProperties.PasswordStrategy.Mode mode = strategy.getMode();
        String customize = strategy.getCustomize();
        switch (mode) {
            case RANDOM:
                origin = UUID.randomUUID().toString();
                password = md5f2(origin);
                break;
            case CIPHERTEXT:
                if (StringUtils.isNotBlank(parameter)) {
                    origin = null;
                    password = parameter;
                } else {
                    origin = customize;
                    password = md5f2(customize);
                }
                break;
            default:
                origin = customize;
                password = md5f2(customize);
        }
        return new Password(origin, password);
    }

    @Override
    public List<UserProfile> getUserProfiles(LoginUser operator, List<String> orgIds) {
        return userMapper.selectUserProfiles(operator.getTenantId(), orgIds);
    }

    @Override
    public Set<String> getSubOrganizationIds(String orgId, LoginUser operator) {
        Set<String> orgIds = new HashSet<>();

        if (StringUtils.isNotBlank(orgId)) {
            orgIds.add(orgId);
            orgIds.addAll(organizationService.getChildrenById(orgId));
        } else {

            orgIds.addAll(
                    ((UserPrincipal) operator).isAdmin()
                            ? Collections.emptyList()
                            : organizationService.getSubOrganizationIds(operator));
        }
        return orgIds;
    }

    @Override
    public Map<String, UserInfoEntity> getUserProps(Set<String> names) {
        List<UserInfoEntity> userInfoEntity = userInfoMapper.selectByIds(names);
        Map<String, UserInfoEntity> userInfoMap = Maps.newHashMap();
        if (CollectionUtils.isNotEmpty(userInfoEntity)) {
            userInfoEntity.forEach(item -> userInfoMap.put(item.getUsername(), item));
        }
        return userInfoMap;
    }

    @Override
    public List<String> getSuperiors(String uid, Integer rank) {
        return List.of();
    }

    /**
     * 增加用户 新增字段信息
     *
     * @param personal 字段map
     * @param username 用户id
     */
    @Override
    public void addUserFields(Map<String, String> personal, String username) {
        Assert.notNull(personal, "data must not be null");
        List<UserFieldsEntity> userFieldDOS = this.convertPersonBean(personal, username);
        userFieldsMapper.insert(userFieldDOS);
    }

    @Override
    public List<User> getUsersByTenantId(String tenantId) {
        Assert.notNull(tenantId, "租户id不能为空");
        return userMapper.selectUsersByTenantId(tenantId);
    }

    @Override
    public void updateTenantUser(User source, LoginUser operator) {
        String username = source.getUsername();
        User target = userMapper.selectUserByUsername(username);
        Assert.notNull(target, "user not found");
        BeanUtils.copyProperties(source, target);
        userMapper.updateUser(target);
        userRoleMapper.deleteUserRoles(username);
        // todo addUserRoles(target, operator.getTenantId());
    }

    @Override
    public void exportMutableUsers(Page<User> pageable, UserSearchParams parameters) {}
}
