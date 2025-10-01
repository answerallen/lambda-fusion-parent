package com.lambda.fusion.authority.user.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.pinyin.PinyinUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.fusion.authority.AuthorityConstants;
import com.lambda.fusion.authority.AuthorityProperties;
import com.lambda.fusion.authority.organization.mapper.OrganizationMapper;
import com.lambda.fusion.authority.organization.model.Org;
import com.lambda.fusion.authority.organization.model.Organization;
import com.lambda.fusion.authority.organization.model.UserOrganization;
import com.lambda.fusion.authority.organization.service.OrganizationService;
import com.lambda.fusion.authority.role.mapper.RoleMapper;
import com.lambda.fusion.authority.role.model.SimpleRole;
import com.lambda.fusion.authority.tenant.persistence.TenantMapper;
import com.lambda.fusion.authority.user.mapper.*;
import com.lambda.fusion.authority.user.model.*;
import com.lambda.fusion.authority.user.model.dto.ResetPwdDTO;
import com.lambda.fusion.authority.user.model.dto.UserCreateDTO;
import com.lambda.fusion.authority.user.model.entity.*;
import com.lambda.fusion.authority.user.model.vo.MutableUserVO;
import com.lambda.fusion.authority.user.service.UserService;
import com.lambda.fusion.core.Constants;
import com.lambda.fusion.core.user.User;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.lambda.fusion.authority.AuthorityConstants.CACHE_MANAGER;
import static com.lambda.fusion.authority.AuthorityConstants.MANAGED;
import static com.lambda.fusion.core.Constants.ROLE_DEV;
import static com.lambda.fusion.core.tree.Tree.SPLIT;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final TenantMapper tenantMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthorityProperties properties;
    private final UserInfoMapper userInfoMapper;
    private final UserFieldsMapper userFieldsMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final UserUpdatePwdLogMapper userUpdatePwdLogMapper;
    private final OrganizationService organizationService;
    protected final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 用户新增字段key
     */
    private static final String USER_PERSONAL = "personal";
    private OrganizationMapper organizationMapper;

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
        return userMapper.getUserNamesByAuthority(orgid, authority);
    }

    @Override
    public List<String> getUserNamesByOrgId(String orgid, Integer type) {
        return userMapper.getUsernameByOrgId(orgid, type);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<MutableUserVO> getAllUsers() {
        return userMapper.getAllUsers();
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MutableUserVO getMutableUserByUsername(String username) {
        Assert.notNull(username, "username is not empty");
        MutableUserVO user = userMapper.getMutableUserById(username);
        if (user != null) {
            //            user.setOnline(laAuthorizeHelper.isOnline(username));
            //            user.setLocked(laAuthorizeHelper.getLockedState(username));
            //            UserInfo props = decorator.getTargetPropsById(username);
            //            user.setProps(props);
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
    public MutableUserVO getCurrentMutableUser(User operator) {
        MutableUserVO mutableUser = this.getMutableUserByUsername(operator.getName());
        UserInfo props = mutableUser.getProps();
        if (props != null && properties.getPasswordStrategy().getEnablePeriodChange()) {
            boolean notMatched =
                    !operator.isDev() && !operator.isAdmin() && !operator.isManager() && !operator.isTenantManager();
            // 判断密码是否需要更新
            if (notMatched && ObjectUtil.equals(props.getUpdatePwd(), false)) {
                List<UserUpdatePwdLogEntity> userUpdatePwdLogEntities =
                        userUpdatePwdLogMapper.selectList(new LambdaQueryWrapper<UserUpdatePwdLogEntity>()
                                .select(UserUpdatePwdLogEntity::getUpdateTime)
                                .eq(UserUpdatePwdLogEntity::getUserName, operator.getName())
                                .isNotNull(UserUpdatePwdLogEntity::getUpdateTime)
                                .orderByDesc(UserUpdatePwdLogEntity::getUpdateTime));
                if (CollectionUtils.isNotEmpty(userUpdatePwdLogEntities)) {
                    UserUpdatePwdLogEntity userUpdatePwdLogEntity = userUpdatePwdLogEntities.getFirst();
                    Integer passwordModifyDays =
                            properties.getPasswordStrategy().getPeriodChangeDays();
                    LocalDateTime nowTime = LocalDateTime.now();
                    LocalDateTime lastUpdateTime = DateUtil.toLocalDateTime(userUpdatePwdLogEntity.getUpdateTime());
                    long days = ChronoUnit.DAYS.between(lastUpdateTime, nowTime);
                    if (days >= passwordModifyDays) {
                        props.setUpdatePwd(true);
                        props.setPasswordModifyDays((int) days);
                        mutableUser.setProps(props);
                    }
                }
            }
        }
        return mutableUser;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<MutableUserVO> getAllMutableUsers(List<MutableUserVO> users) {
        if (CollectionUtils.isEmpty(users)) {
            return users;
        }
        return userMapper.getAllMutableUsers(users);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Page<MutableUserVO> getAllMutableUsers(Page<MutableUserVO> pagination, Map<String, Object> parameters) {
        String tenantId = MapUtils.getString(parameters, "tenant_id");

        // 预处理查询参数
        preprocessQueryParameters(parameters);

        // 执行分页查询
        pagination = userMapper.getAllMutableUsersByCondition(pagination, parameters);
        List<MutableUserVO> users = pagination.getRecords();

        if (CollectionUtils.isNotEmpty(users)) {
            // 补充用户详细信息
            List<MutableUserVO> enrichedUsers = enrichUserDetails(users, tenantId);
            pagination.setRecords(enrichedUsers);
        }

        return pagination;
    }

    /**
     * 预处理查询参数
     */
    private void preprocessQueryParameters(Map<String, Object> parameters) {
        setUsernames(parameters);
        setUserFields(parameters);
    }

    /**
     * 丰富用户详细信息
     */
    private List<MutableUserVO> enrichUserDetails(List<MutableUserVO> users, String tenantId) {
        List<MutableUserVO> records = userMapper.getAllMutableUsers(users);
        UserTempParameters tempParams = getUserTempParameters(records);

        // 批量获取关联数据
        Map<String, String> orgNames = getOrgFullNamesByOrgIds(tempParams.getOrgIds());
        Map<String, Map<String, String>> personInfo = getPersonsByUids(tempParams.getUids());

        // 补充用户信息
        for (MutableUserVO user : records) {
            enrichSingleUserInfo(user, orgNames, personInfo, tenantId);
        }

        return records;
    }

    /**
     * 补充单个用户的详细信息
     */
    private void enrichSingleUserInfo(
            MutableUserVO user,
            Map<String, String> orgNames,
            Map<String, Map<String, String>> personInfo,
            String tenantId) {
        supplementUserOrgInfo(orgNames, user);
        supplementUserPersonInfo(personInfo, user);
        supplementUserLockState(user);
        supplementUserPermissionInfo(user, tenantId);

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
     *
     * @param users
     */
    private UserTempParameters getUserTempParameters(List<MutableUserVO> users) {
        Set<String> uids = Sets.newHashSet();
        Set<String> orgIds = Sets.newHashSet();
        for (MutableUserVO item : users) {
            uids.add(item.getUsername());
            if (isBindOrg(item)) {
                orgIds.add(item.getOrg().getId());
            }
        }
        UserTempParameters parameters = new UserTempParameters();
        parameters.setUids(uids);
        parameters.setOrgIds(orgIds);
        return parameters;
    }

    /**
     * 补充完善用户权限信息
     *
     * @param item
     * @param tenantId
     * @return void
     */
    private void supplementUserPermissionInfo(MutableUserVO item, String tenantId) {
        // todo       if (RoleUtil.isTenant(item)) {
        //            item.setDisAllocation(true);
        //        }
        extractedPermission(tenantId, item);
    }

    /**
     * 补充完善用户锁定信息
     *
     * @param item
     */
    private void supplementUserLockState(MutableUserVO item) {
        // 锁定状态
    }

    /**
     * 补充用户在线信息
     *
     * @param onlines
     * @param item
     * @param uid     当前登陆用户编号
     */
    private void supplementUserOnlineInfo(Set<String> onlines, MutableUserVO item, String uid) {
        String username = item.getUsername();
        item.setOnline(onlines.contains(username));
        boolean self = username.equals(uid);
        item.setSelf(self);
        if (self) {
            // 执行操作的用户必定为在线状态
            item.setOnline(true);
        }
    }

    private void supplementUserPersonInfo(Map<String, Map<String, String>> allPersonUserMap, MutableUserVO item) {
        if (allPersonUserMap.containsKey(item.getUsername())) {
            item.setPersonal(allPersonUserMap.get(item.getUsername()));
        }
    }

    /**
     * 补充完善用户组织信息
     *
     * @param orgnames
     * @param item
     */
    private void supplementUserOrgInfo(Map<String, String> orgnames, MutableUserVO item) {
        if (isBindOrg(item)) {
            Org org = item.getOrg();
            org.setFullName(orgnames.getOrDefault(org.getId(), org.getAlias()));
        }
    }

    /**
     * 是否绑定了组织机构
     *
     * @param user0
     * @return boolean
     */
    private boolean isBindOrg(@NonNull MutableUserVO user0) {
        Org org = user0.getOrg();
        return org != null && StringUtils.isNotBlank(org.getId());
    }

    /**
     * 获取组织全名
     *
     * @param orgIds
     * @return
     */
    private Map<String, String> getOrgFullNamesByOrgIds(Set<String> orgIds) {
        if (CollectionUtils.isEmpty(orgIds)) {
            return Collections.emptyMap();
        }
        List<Organization> organizations = organizationMapper.getOrgIdsByIds(orgIds);
        if (CollectionUtils.isEmpty(organizations)) {
            return Collections.emptyMap();
        }
        Map<String, String> map1 = Maps.newHashMap();
        Map<String, String> names0 = Maps.newHashMap();
        Set<String> parentKeys = Sets.newHashSet();
        for (Organization item : organizations) {
            String parentKeys1 = item.getParentKeys();
            names0.put(item.getId(), item.getAlias());
            map1.put(item.getId(), parentKeys1);
            if (StringUtils.isNotBlank(parentKeys1)) {
                Collections.addAll(parentKeys, parentKeys1.split(SPLIT));
            }
        }
        Map<String, String> result = Maps.newHashMap();
        if (CollectionUtils.isEmpty(parentKeys)) {
            return result;
        }
        List<Organization> parents = organizationMapper.getOrgIdsByIds(parentKeys);
        Map<String, String> names1 =
                parents.stream().collect(Collectors.toMap(Organization::id, Organization::getName));
        map1.forEach((key, value) -> {
            StringBuilder builder = new StringBuilder();
            if (StringUtils.isNotBlank(value)) {
                for (String token : value.split(SPLIT)) {
                    builder.append(names1.get(token)).append(SPLIT);
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

    private void setUsernames(Map<String, Object> parameters) {
        final String usernames = "username";
        String ids = MapUtils.getString(parameters, usernames);
        if (StringUtils.isNotBlank(ids)) {
            String[] split = ids.split(Constants.DELIMITER);
            List<String> strings = Arrays.asList(split);
            parameters.put(usernames, strings);
        } else {
            parameters.put(usernames, null);
        }
    }

    /***
     * 验证是否有权限操作
     * @param tenantId 租户id
     * @param item 当前用户
     */
    private void extractedPermission(String tenantId, MutableUserVO item) {
        if (StringUtils.isNotBlank(item.getTenantId()) && !Objects.equals(tenantId, item.getOwner())) {
            item.setNoPermission(true);
        }
    }

    @Override
    public List<String> getUidsByOrg(String orgid, String roleid) {
        return userMapper.getUidsByOrg(orgid, roleid);
    }

    @Override
    public Set<String> getPermissions(LoginUser operator, String source) {
        return userMapper.getUserPermissions(source);
    }

    @Override
    public void batchSavePermissions(LoginUser operator, String target, Set<String> permissions) {
        String tenantId = operator.getTenantId();
        // TODO 批量保存权限
        for (String permission : permissions) {
            userMapper.addUserRole(target, permission, tenantId);
        }
    }

    @Override
    public void batchSavePermissions(LoginUser operator, String source, String target, Set<String> permissions) {
        if (CollectionUtils.isEmpty(permissions)) {
            return;
        }
        List<RoleResources> insertResources = userMapper.getRoleResources(source, null, permissions);
        String tenantId = operator.getTenantId();
        // TODO 批量保存权限性能优化
        for (RoleResources roleResources : insertResources) {
            userMapper.saveUserPermission(target, roleResources, tenantId);
        }
    }

    @Override
    public void batchUpdatePermissions(LoginUser operator, String source, String target, Set<String> permissions) {
        List<RoleResources> updateResources = userMapper.getRoleResources(source, MANAGED, permissions);
        if (CollectionUtils.isNotEmpty(updateResources)) {
            userMapper.batchUpdateUserPermissions(target, MANAGED, updateResources, operator.getTenantId());
        }
    }

    @Override
    public List<Permission> getUserPermissions(String username, String mode) {
        Assert.notNull(username, "username not empty");
        MutableUserVO user = userMapper.getMutableUserById(username);
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
        return userMapper.getAllUserPermissions(ids, mode);
    }

    @CacheEvict(value = "LAResourceOwners", allEntries = true, cacheManager = CACHE_MANAGER)
    @Override
    public String addUser(UserCreateDTO userCreateDTO, LoginUser operator) {
        UserEntity userEntity = userCreateDTO.toEntity();
        Assert.notNull(userEntity, "user is not null");

        boolean hasExists = userMapper.hasExists(userEntity.getUserid());
        Assert.isTrue(hasExists, "该用户名已被使用");

        AuthorityProperties.PasswordStrategy strategy = properties.getPasswordStrategy();
        String originPassword = userEntity.getPassword();
        Password encodePassword = obtainPassword(strategy, originPassword);
        userEntity.setPassword(passwordEncoder.encode(encodePassword.getEncrypted()));

        userEntity.setCreateDate(new Date());
        userEntity.setNicknameAbbr(PinyinUtil.getPinyin(userEntity.getNickname()));
        userEntity.setTenantId(operator.getTenantId());
        userEntity.setOwner(operator.getTenantId());
        userEntity.setCreator(operator.getName());
        userMapper.insert(userEntity);


        List<SimpleRole> roles = userCreateDTO.getAuthorities();
        addUserRoles(operator, roles, userEntity);

        if (Objects.isNull(userCreateDTO.getProps())) {
            userCreateDTO.setProps(new UserInfo());
        }
        userCreateDTO.getProps().setUpdatePwd(true);

        Org org = userCreateDTO.getOrg();
        if (org != null && StringUtils.isNotBlank(org.getId())) {
            userOrganizationMapper.insert(new UserOrganizationEntity(userEntity.getUserid(), org.getId(), operator.getTenantId()));
        }
        return encodePassword.getOrigin();
    }

    private void addUserRoles(LoginUser operator, List<SimpleRole> roles, UserEntity userEntity) {
        if (CollectionUtils.isNotEmpty(roles)) {
            List<UserRole> userRoles = roles.stream()
                    .map(SimpleRole::getAuthority)
                    .filter(StrUtil::isNotEmpty)
                    .map(authority -> {
                        Assert.notNull(authority, "role not found");
                        if (authority.startsWith(Constants.ROLE_TENANT)) {
                            authority = Constants.ROLE_TENANT;
                        }
                        UserRole userRole = new UserRole();
                        userRole.setAuthority(authority);
                        userRole.setTenantId(operator.getTenantId());
                        userRole.setUserid(userEntity.getUserid());
                        return userRole;
                    }).collect(Collectors.toList());
            userRoleMapper.insert(userRoles);
        }
    }

    @CacheEvict(value = "LAResourceOwners", allEntries = true, cacheManager = CACHE_MANAGER)
    @Override
    public void updateUser(MutableUserVO source, LoginUser operator) {
        Assert.notNull(source, "user is not null");
        Assert.notNull(source.getUsername(), "username not empty");
        String username = source.getUsername();

        UserEntity userEntity = userMapper.selectById(username);

        MutableUserVO target = userMapper.getMutableUserById(username);
        Assert.notNull(target, "user not empty");
        Org original = target.getOrg();
        // 如果要修改的用户是租户角色，则不允许修改其组织
//        if (!source) {
//        updateUserOrg(username, source.getOrg(), original, operator);
//        }

        checkPropsUpdated(source.getProps(), target.getProps());

        BeanUtils.copyProperties(source, target);
        target.setNicknameAbbr(PinyinUtil.getPinyin(target.getNickname()));
        userMapper.updateMutableUser(target);
        userMapper.deleteUserRoles(username);
        addUserRoles(, operator.getTenantId(),userEntity);
        if (MapUtils.isNotEmpty(source.getPersonal())) {
            List<UserFieldsEntity> fields = this.convertPersonBean(source.getPersonal(), username);
            this.userFieldsMapper.deleteByUsername(username);
            this.userFieldsMapper.insert(fields);
        }
    }

    /**
     * 修改用户组织关系
     *
     * @param username 要修改的用户编号
     * @param source   前端传递的组织机构
     * @param original 原组织机构
     * @param operator 当前用户
     * @return void
     */
    private void updateUserOrg(String username, Org source, Org original, LoginUser operator) {
        Org updated = orgUpdated(source, original);
        if (null != updated) {
            if (StringUtils.isNotBlank(updated.getId())) {
                UserOrganization changed = new UserOrganization(username, updated.getId(), operator.getTenantId());
                if (StringUtils.isNotBlank(getOrgId(original))) {
                    organizationMapper.updateUserOrganization(changed);
                } else {
                    organizationMapper.addUserOrganization(changed);
                }
            } else {
                organizationMapper.deleteUserOrganizationByUser(username);
            }
        }
    }

    /**
     * 判断扩展属性是否变化
     *
     * @param source 原始用户信息
     * @param actual 当前用户信息
     * @return 是否需要更新扩展属性
     */
    private boolean checkPropsUpdated(UserInfo source, UserInfo actual) {
        if (source == null || actual == null) {
            return false;
        }

        // 比较关键属性是否发生变化
        return !Objects.equals(source.getAvatar(), actual.getAvatar());
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
    private Org orgUpdated(Org source, Org target) {
        if (source == null) {
            return new Org();
        }
        if (target == null) {
            return source;
        }
        String id0 = source.getId();
        String id1 = target.getId();
        if (StringUtils.isBlank(id0)) {
            return new Org();
        } else if (id0.equals(id1)) {
            return null;
        } else {
            return source;
        }
    }

    /**
     * 获取orgId
     *
     * @param org 组织对象
     * @return java.lang.String
     */
    private String getOrgId(Org org) {
        if (org != null && StringUtils.isNotBlank(org.getId())) {
            return org.getId();
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(LoginUser operator, String username) {
        Assert.notNull(username, "username not empty");
        Assert.isTrue(!username.equals(operator.getName()), "lambda.authority.no.operation.authority");
        boolean exists = userMapper.hasExists(username);
        if (exists) {
            userMapper.deleteUser(username);
            userMapper.deleteUserRoles(username);
            roleMapper.deleteResourceRoleByAuthority(username);
            organizationMapper.deleteUserOrganizationByUser(username);
            userFieldsMapper.deleteByUsername(username);
            // todo 删除用户数据权限
        }
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<MutableUserVO> getAllMutableUsersByKey(String key) {
        Assert.notNull(key, "key not empty");
        return userMapper.getAllMutableUsersByKey(key);
    }


    @Override
    public void updateUserPassword(String username, String oldpassword, String newpassword) {
        MutableUserVO mutableUser = userMapper.getPasswordById(username);
        Assert.notNull(mutableUser, "user not found");
        boolean isChecked = passwordEncoder.matches(oldpassword, mutableUser.getPassword());
        Assert.isTrue(isChecked, "lambda.authority.user.password.incorrect");
        mutableUser.setPassword(passwordEncoder.encode(newpassword));
        UserUpdatePwdLogEntity userUpdatePwdLogEntity = new UserUpdatePwdLogEntity();
        userUpdatePwdLogEntity.setPassWord(passwordEncoder.encode(newpassword));
        userUpdatePwdLogEntity.setUserName(username);
        userUpdatePwdLogMapper.insertLog(userUpdatePwdLogEntity);
        userMapper.updatePassword(mutableUser);
        userInfoMapper.updateStatus(username, false);
        // todo 初始化令牌
    }

    @Override
    public String resetUserPassword(ResetPwdDTO resetPwdDTO) {
        AuthorityProperties.PasswordStrategy strategy = properties.getPasswordStrategy();
        String parameter = resetPwdDTO.getNewPassword();
        Password password = obtainPassword(strategy, parameter);
        MutableUserVO mutableUser = new MutableUserVO();
        mutableUser.setUsername(resetPwdDTO.getUsername());
        mutableUser.setPassword(passwordEncoder.encode(password.getEncrypted()));
        userMapper.updatePassword(mutableUser);
        UserInfoEntity userInfoEntity = new UserInfoEntity();
        userInfoEntity.setUserid(resetPwdDTO.getUsername());
        userInfoEntity.setUpdatePwd(true);
        int count = userInfoMapper.updateStatus(resetPwdDTO.getUsername(), true);
        if (0 == count) {
            userInfoMapper.insert(userInfoEntity);
        }
        // todo 初始化令牌
        return password.getOrigin();
    }

    @Override
    public void prohibitUser(LoginUser operator, Integer type, String username) {
        Assert.notNull(username, "username not null");
        MutableUserVO user = userMapper.getMutableUserById(username);
        Assert.notNull(user, "user not found");
        Assert.isTrue(!operator.getName().equals(username), "lambda.authority.no.operation.authority");
        userMapper.prohibitUser(type, username);
    }

    @Override
    public void unlockUser(String username, LoginUser operator) {
    }

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
    public List<SimpleUser> getAllSimpleUser(LoginUser operator, List<String> orgIds) {
        return userMapper.getAllSimpleUser(operator.getTenantId(), orgIds);
    }

    @Override
    public Set<String> getSubOrgIds(String orgid, LoginUser operator) {
        Set<String> orgIds = new HashSet<>();
        //      todo  boolean admin = OperatorUtils.isAdmin(operator);
        boolean admin = true;
        if (StringUtils.isNotBlank(orgid)) {
            orgIds.add(orgid);
            orgIds.addAll(organizationService.getChildrenById(orgid));
        } else {
            orgIds.addAll(admin ? Collections.emptyList() : organizationService.getSubordinateOrgIds(operator));
        }
        return orgIds;
    }

    @Override
    public Map<String, UserInfoEntity> getUserProps(Set<String> names) {
        List<UserInfoEntity> userInfoEntity = userInfoMapper.selectByIds(names);
        Map<String, UserInfoEntity> userInfoMap = Maps.newHashMap();
        if (CollectionUtils.isNotEmpty(userInfoEntity)) {
            userInfoEntity.forEach(item -> userInfoMap.put(item.getUserid(), item));
        }
        return userInfoMap;
    }

    @Override
    public List<String> getSuperiors(String uid, Integer rank) {
        return List.of();
    }

    // 设置用户字段 todo 待完善
    private void setUserFields(Map<String, Object> parameters) {
        if (parameters.get(USER_PERSONAL) != null) {
            String personal = parameters.get(USER_PERSONAL).toString();
            Map<String, String> tempMap = (Map<String, String>) JSONUtil.parse(personal);
            List<UserFieldsEntity> fields = this.convertPersonBean(tempMap, null);
            parameters.put(USER_PERSONAL, fields);
        }
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
    public List<MutableUserVO> getAllMutableUsersByTenantId(String tenantId) {
        Assert.notNull(tenantId, "租户id不能为空");
        return userMapper.getAllMutableUsersByTenantId(tenantId);
    }

    @Override
    public void updateTenantUser(MutableUserVO source, LoginUser operator) {
        Assert.notNull(source, "user must not be null");
        Assert.notNull(source.getUsername(), "username must not be null");
        String username = source.getUsername();
        MutableUserVO target = userMapper.getMutableUserById(username);
        Assert.notNull(target, "user not found");
        BeanUtils.copyProperties(source, target);
        target.setNicknameAbbr(PinyinUtil.getPinyin(target.getNickname()));
        userMapper.updateMutableUser(target);
        userMapper.deleteUserRoles(username);
        addUserRoles(target, operator.getTenantId());
    }

    @Override
    public void exportMutableUsers(Page<MutableUserVO> pageable, Map<String, Object> parameters) {
    }
}
