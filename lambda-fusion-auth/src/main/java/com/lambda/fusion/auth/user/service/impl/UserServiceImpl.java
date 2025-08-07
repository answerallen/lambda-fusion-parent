package com.lambda.fusion.auth.user.service.impl;

import static com.lambda.fusion.autoconfig.AuthorizeConstants.CACHE_MANAGER;
import static com.lambda.fusion.autoconfig.AuthorizeConstants.MANAGED;
import static com.lambda.fusion.core.Constants.ROLE_DEV;
import static com.lambda.fusion.core.tree.Tree.SPLIT;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.extra.pinyin.PinyinUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.fusion.auth.organization.domain.Org;
import com.lambda.fusion.auth.organization.domain.Organization;
import com.lambda.fusion.auth.organization.domain.UserOrganization;
import com.lambda.fusion.auth.organization.mapper.OrganizationMapper;
import com.lambda.fusion.auth.organization.service.OrganizationService;
import com.lambda.fusion.auth.role.bean.SimpleRole;
import com.lambda.fusion.auth.role.mapper.RoleMapper;
import com.lambda.fusion.auth.tenant.persistence.TenantMapper;
import com.lambda.fusion.auth.user.domain.*;
import com.lambda.fusion.auth.user.mapper.UserFieldsMapper;
import com.lambda.fusion.auth.user.mapper.UserInfoMapper;
import com.lambda.fusion.auth.user.mapper.UserMapper;
import com.lambda.fusion.auth.user.mapper.UserUpdatePwdLogMapper;
import com.lambda.fusion.auth.user.service.UserService;
import com.lambda.fusion.autoconfig.AuthorizeConstants;
import com.lambda.fusion.autoconfig.AuthorizeProperties;
import com.lambda.fusion.core.Constants;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthorizeProperties properties;

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private UserFieldsMapper userFieldsMapper;

    @Autowired
    private OrganizationMapper organizationMapper;

    @Autowired
    private UserUpdatePwdLogMapper userUpdatePwdLogMapper;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    protected ApplicationEventPublisher applicationEventPublisher;

    /**
     * 用户新增字段key
     */
    private static final String USER_PERSONAL = "personal";

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
        if (!authority.equals(AuthorizeConstants.ROLE_MANAGER)) {
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
    public List<MutableUser> getAllUsers() {
        return userMapper.getAllUsers();
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MutableUser getMutableUserByUsername(String username) {
        Assert.notNull(username, AuthorizeConstants.USER_NAME_NOT_EMPTY);
        MutableUser user = userMapper.getMutableUserById(username);
        if (user != null) {
            //            user.setOnline(laAuthorizeHelper.isOnline(username));
            //            user.setLocked(laAuthorizeHelper.getLockedState(username));
            //            UserInfo props = decorator.getTargetPropsById(username);
            //            user.setProps(props);
            List<UserFields> fields = userFieldsMapper.getListByUsername(username);
            Map<String, Map<String, String>> allPersonUserMap;
            if (CollectionUtils.isNotEmpty(fields)) {
                allPersonUserMap = this.convertPersonMap(fields);
                user.setPersonal(allPersonUserMap.get(username));
            }
        }
        return user;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @SuppressWarnings("java:S6809")
    @Override
    public MutableUser getCurrentMutableUser(LoginUser operator) {
        MutableUser mutableUser = this.getMutableUserByUsername(operator.getUsername());
        UserInfo props = mutableUser.getProps();
        if (props != null && properties.getPasswordStrategy().getEnablePeriodChange()) {
            //            boolean notMatched = !OperatorUtils.isDev(operator) && !OperatorUtils.isAdmin(operator) &&
            // !OperatorUtils.isManager(operator) && !OperatorUtils.isTenantManager(operator);
            boolean notMatched = true;
            // 判断密码是否需要更新
            if (notMatched && ObjectUtil.equals(props.getUpdatePwd(), false)) {
                List<UserUpdatePwdLog> userUpdatePwdLogs =
                        userUpdatePwdLogMapper.selectList(new LambdaQueryWrapper<UserUpdatePwdLog>()
                                .select(UserUpdatePwdLog::getUpdateTime)
                                .eq(UserUpdatePwdLog::getUserName, operator.getUsername())
                                .isNotNull(UserUpdatePwdLog::getUpdateTime)
                                .orderByDesc(UserUpdatePwdLog::getUpdateTime));
                if (CollectionUtils.isNotEmpty(userUpdatePwdLogs)) {
                    UserUpdatePwdLog userUpdatePwdLog = userUpdatePwdLogs.get(0);
                    Integer passwordModifyDays =
                            properties.getPasswordStrategy().getPeriodChangeDays();
                    LocalDateTime nowTime = LocalDateTime.now();
                    LocalDateTime lastUpdateTime = DateUtil.toLocalDateTime(userUpdatePwdLog.getUpdateTime());
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
    public List<MutableUser> getAllMutableUsers(List<MutableUser> users) {
        if (CollectionUtils.isEmpty(users)) {
            return users;
        }
        return userMapper.getAllMutableUsers(users);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Page<MutableUser> getAllMutableUsers(Page<MutableUser> pagination, Map<String, Object> parameters) {
        // 当前登陆用户编号
        String uid = MapUtils.getString(parameters, "uid");
        String tenantId = MapUtils.getString(parameters, "tenant_id");
        setUsernames(parameters);
        setUserFields(parameters);
        pagination = userMapper.getAllMutableUsersByCondition(pagination, parameters);
        List<MutableUser> users = pagination.getRecords();
        if (CollectionUtils.isNotEmpty(users)) {
            List<MutableUser> records = userMapper.getAllMutableUsers(users);
            UserTempParameters temp0 = getUserTempParameters(records);
            Set<String> uids = temp0.getUids();
            Set<String> orgids = temp0.getOrgids();
            Map<String, String> orgnames = getOrgFullNamesByOrgids(orgids);
            Map<String, Map<String, String>> persons = getPersonsByUids(uids);
            for (MutableUser item : records) {
                supplementUserOrgInfo(orgnames, item);
                supplementUserPersonInfo(persons, item);
                supplementUserLockState(item);
                supplementUserPermissionInfo(item, tenantId);
                item.getAuthorities().sort(Comparator.comparing(SimpleRole::getAuthority));
            }
            pagination.setRecords(records);
        }
        return pagination;
    }

    private Map<String, Map<String, String>> getPersonsByUids(Set<String> uids) {
        List<UserFields> fields = userFieldsMapper.getPersonUser(uids);
        return this.convertPersonMap(fields);
    }

    /**
     * 整理用户临时参数
     *
     * @param users
     */
    private UserTempParameters getUserTempParameters(List<MutableUser> users) {
        Set<String> uids = Sets.newHashSet();
        Set<String> orgids = Sets.newHashSet();
        for (MutableUser item : users) {
            uids.add(item.getUsername());
            if (isBindOrg(item)) {
                orgids.add(item.getOrg().getId());
            }
        }
        UserTempParameters parameters = new UserTempParameters();
        parameters.setUids(uids);
        parameters.setOrgids(orgids);
        return parameters;
    }

    /**
     * 补充完善用户权限信息
     *
     * @param item
     * @param tenantId
     * @return void
     */
    private void supplementUserPermissionInfo(MutableUser item, String tenantId) {
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
    private void supplementUserLockState(MutableUser item) {
        // 锁定状态
    }

    /**
     * 补充用户在线信息
     *
     * @param onlines
     * @param item
     * @param uid     当前登陆用户编号
     */
    private void supplementUserOnlineInfo(Set<String> onlines, MutableUser item, String uid) {
        String username = item.getUsername();
        item.setOnline(onlines.contains(username));
        boolean self = username.equals(uid);
        item.setSelf(self);
        if (self) {
            // 执行操作的用户必定为在线状态
            item.setOnline(true);
        }
    }

    private void supplementUserPersonInfo(Map<String, Map<String, String>> allPersonUserMap, MutableUser item) {
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
    private void supplementUserOrgInfo(Map<String, String> orgnames, MutableUser item) {
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
    private boolean isBindOrg(@NonNull MutableUser user0) {
        Org org = user0.getOrg();
        return org != null && StringUtils.isNotBlank(org.getId());
    }

    /**
     * 获取组织全名
     *
     * @param orgids
     * @return
     */
    private Map<String, String> getOrgFullNamesByOrgids(Set<String> orgids) {
        if (CollectionUtils.isEmpty(orgids)) {
            return Collections.emptyMap();
        }
        List<Organization> orgs = organizationMapper.getOrgansByIds(orgids);
        if (CollectionUtils.isEmpty(orgs)) {
            return Collections.emptyMap();
        }
        Map<String, String> map1 = Maps.newHashMap();
        Map<String, String> names0 = Maps.newHashMap();
        Set<String> parentkeys = Sets.newHashSet();
        for (Organization item : orgs) {
            String parentkeys1 = item.getParentkeys();
            names0.put(item.getId(), item.getAlias());
            map1.put(item.getId(), parentkeys1);
            if (StringUtils.isNotBlank(parentkeys1)) {
                Collections.addAll(parentkeys, parentkeys1.split(SPLIT));
            }
        }
        Map<String, String> result = Maps.newHashMap();
        if (CollectionUtils.isEmpty(parentkeys)) {
            return result;
        }
        List<Organization> parents = organizationMapper.getOrgansByIds(parentkeys);
        Map<String, String> names1 =
                parents.stream().collect(Collectors.toMap(Organization::id, Organization::getName));
        map1.forEach((key, value) -> {
            StringBuilder builder = new StringBuilder();
            if (StringUtils.isNotBlank(value)) {
                for (String splited : value.split(SPLIT)) {
                    builder.append(names1.get(splited)).append(SPLIT);
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
    private Map<String, Map<String, String>> convertPersonMap(List<UserFields> fields) {
        Map<String, Map<String, String>> maps = Maps.newHashMap();
        fields.forEach(userFields -> {
            String username = userFields.getUsername();
            Map<String, String> map;
            if (!maps.containsKey(username)) {
                map = Maps.newHashMap();
            } else {
                map = maps.get(username);
            }
            map.put(userFields.getFieldName(), userFields.getFieldValue());
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
    private List<UserFields> convertPersonBean(Map<String, String> personal, String username) {
        List<UserFields> userFields = new ArrayList<>(personal.size());
        personal.forEach((k, v) -> {
            UserFields info = new UserFields();
            info.setUsername(username);
            info.setFieldName(k);
            info.setFieldValue(v);
            userFields.add(info);
        });
        return userFields;
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
    private void extractedPermission(String tenantId, MutableUser item) {
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
        Assert.notNull(username, AuthorizeConstants.USER_NAME_NOT_EMPTY);
        MutableUser user = userMapper.getMutableUserById(username);
        Assert.notNull(user, AuthorizeConstants.USER_NOT_FOUND);
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
    public String addMutableUser(MutableUser user, LoginUser operator) {
        Assert.notNull(user, AuthorizeConstants.USER_NOT_FOUND);
        String username = user.getUsername();
        Assert.notNull(username, AuthorizeConstants.USER_NAME_NOT_EMPTY);
        // TODO 系统保留用户名
        Assert.isTrue(!userMapper.hasExists(username), "该用户名已被使用");
        AuthorizeProperties.PasswordStrategy strategy = properties.getPasswordStrategy();
        String parameter = user.getPassword();
        Password password = obtainPassword(strategy, parameter);
        user.setPassword(passwordEncoder.encode(password.getEncrypted()));
        // TODO 如果是租户
        if (true) {
            String orgid = user.getTenantId();
            user.setOrg(new Org(orgid));
        }
        user.setCreateDate(new Date());
        user.setCreateAccount(operator.getUsername());
        user.setNicknameAbbr(PinyinUtil.getPinyin(user.getNickname()));
        user.setTenantId(operator.getTenantId());
        user.setOwner(operator.getTenantId());
        user.setCreator(operator.getUsername());
        if (Objects.isNull(user.getProps())) {
            user.setProps(new UserInfo());
        }
        user.getProps().setUpdatePwd(true);
        userMapper.insertMutableUser(user);
        addUserRoles(user, operator.getTenantId());
        Org org = user.getOrg();
        if (org != null && StringUtils.isNotBlank(org.getId())) {
            organizationMapper.addUserOrganization(new UserOrganization(username, org.getId(), operator.getTenantId()));
        }
        return password.getOrigin();
    }

    @CacheEvict(value = "LAResourceOwners", allEntries = true, cacheManager = CACHE_MANAGER)
    @Override
    public void updateMutableUser(MutableUser source, LoginUser operator) {
        Assert.notNull(source, AuthorizeConstants.USER_NOT_FOUND);
        Assert.notNull(source.getUsername(), AuthorizeConstants.USER_NAME_NOT_EMPTY);
        String username = source.getUsername();
        MutableUser target = userMapper.getMutableUserById(username);
        Assert.notNull(target, AuthorizeConstants.USER_NOT_FOUND);
        Org original = target.getOrg();
        // 如果要修改的用户是租户角色，则不允许修改其组织
        //        if (!RoleUtil.isTenant(source)) {
        updateUserOrg(username, source.getOrg(), original, operator);
        //        }
        // todo 对比现属性和原属性差别，判断是否需要修改扩展属性
        checkPropsUpdated(source.getProps(), target.getProps());

        BeanUtils.copyProperties(source, target);
        target.setNicknameAbbr(PinyinUtil.getPinyin(target.getNickname()));
        userMapper.updateMutableUser(target);
        userMapper.deleteUserRoles(username);
        addUserRoles(target, operator.getTenantId());
        if (MapUtils.isNotEmpty(source.getPersonal())) {
            List<UserFields> fields = this.convertPersonBean(source.getPersonal(), username);
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
     * @return java.util.Map<java.lang.String, java.lang.Object>
     */
    private Object checkPropsUpdated(UserInfo source, UserInfo actual) {
        return null;
    }

    /**
     * @param user 当前用户
     **/
    @Override
    public void registeredMutableUser(MutableUser user) {
        Assert.isTrue(properties.isEnabledRegistered(), "User registration is not enabled!");
        Assert.notNull(user, AuthorizeConstants.USER_NOT_FOUND);
        String username = user.getUsername();
        // todo 系统保留用户名
        Assert.isTrue(!userMapper.hasExists(username), "用户名已存在");
        String password = passwordEncoder.encode(md5f2(user.getPassword()));
        String orgId = getOrgId(user.getOrg());
        Assert.notNull(orgId, "lambda.authority.organ.id.notempty");
        Organization org = organizationMapper.queryOrganizationById(orgId);
        String owner = org.getOwner();
        user.setPassword(password);
        user.setCreateDate(new Date());
        user.setTenantId(owner);
        user.setNicknameAbbr(PinyinUtil.getPinyin(user.getNickname()));
        userMapper.insertMutableUser(user);
        addUserRoles(user, org.getTenantId());

        organizationMapper.addUserOrganization(new UserOrganization(username, orgId, org.getTenantId()));
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
        Assert.notNull(username, AuthorizeConstants.USER_NAME_NOT_EMPTY);
        Assert.isTrue(!username.equals(operator.getUsername()), "lambda.authority.no.operation.authority");
        boolean exists = userMapper.hasExists(username);
        if (exists) {
            MutableUser target = new MutableUser();
            target.setUsername(username);
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
    public List<MutableUser> getAllMutableUsersByKey(String key) {
        Assert.notNull(key, "lambda.authority.user.searchkey.notempty");
        return userMapper.getAllMutableUsersByKey(key);
    }

    /**
     * 插入用户角色信息，由于需要兼容oracle，只能单条插入
     *
     * @param user 用户对象
     */
    private void addUserRoles(MutableUser user, String tenantId) {
        List<SimpleRole> roles = user.getAuthorities();
        if (CollectionUtils.isNotEmpty(roles)) {
            for (SimpleRole role : roles) {
                String authority = role.getAuthority();
                Assert.notNull(authority, AuthorizeConstants.ROLE_NAME_NOT_EMPTY);
                //                if (authority.startsWith(Constants.ROLE_TENANT)) {
                //                    authority = Constants.ROLE_TENANT;
                //                }
                userMapper.addUserRole(user.getUsername(), authority, tenantId);
            }
        }
    }

    @Override
    public void updateUserPassword(String username, String oldpassword, String newpassword) {
        MutableUser mutableUser = userMapper.getPasswordById(username);
        Assert.notNull(mutableUser, AuthorizeConstants.USER_NOT_FOUND);
        boolean isChecked = passwordEncoder.matches(oldpassword, mutableUser.getPassword());
        Assert.isTrue(isChecked, "lambda.authority.user.password.incorrect");
        mutableUser.setPassword(passwordEncoder.encode(newpassword));
        UserUpdatePwdLog userUpdatePwdLog = new UserUpdatePwdLog();
        userUpdatePwdLog.setPassWord(passwordEncoder.encode(newpassword));
        userUpdatePwdLog.setUserName(username);
        userUpdatePwdLogMapper.insertLog(userUpdatePwdLog);
        userMapper.updatePassword(mutableUser);
        userInfoMapper.updateStatus(username, false);
        // todo 初始化令牌
    }

    @Override
    public String resetUserPassword(ResetPwdParameter resetPwdParameter) {
        AuthorizeProperties.PasswordStrategy strategy = properties.getPasswordStrategy();
        String parameter = resetPwdParameter.getNewPassword();
        Password password = obtainPassword(strategy, parameter);
        MutableUser mutableUser = new MutableUser();
        mutableUser.setUsername(resetPwdParameter.getUsername());
        mutableUser.setPassword(passwordEncoder.encode(password.getEncrypted()));
        userMapper.updatePassword(mutableUser);
        UserInfoDO userInfoDO = new UserInfoDO();
        userInfoDO.setUserid(resetPwdParameter.getUsername());
        userInfoDO.setUpdatePwd(true);
        int count = userInfoMapper.updateStatus(resetPwdParameter.getUsername(), true);
        if (0 == count) {
            userInfoMapper.insert(userInfoDO);
        }
        // todo 初始化令牌
        return password.getOrigin();
    }

    @Override
    public void prohibitUser(LoginUser operator, Integer type, String username) {
        Assert.notNull(username, AuthorizeConstants.USER_NAME_NOT_EMPTY);
        MutableUser user = userMapper.getMutableUserById(username);
        Assert.notNull(user, AuthorizeConstants.USER_NOT_FOUND);
        Assert.isTrue(!operator.getUsername().equals(username), "lambda.authority.no.operation.authority");
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
    static Password obtainPassword(AuthorizeProperties.PasswordStrategy strategy, String parameter) {
        String origin;
        String password;
        AuthorizeProperties.PasswordStrategy.Mode mode = strategy.getMode();
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
    public List<SimpleUser> getAllSimpleUser(LoginUser operator, List<String> organs) {
        return userMapper.getAllSimpleUser(operator.getTenantId(), organs);
    }

    @Override
    public Set<String> getSubOrgans(String orgid, LoginUser operator) {
        Set<String> organs = new HashSet<>();
        //      todo  boolean admin = OperatorUtils.isAdmin(operator);
        boolean admin = true;
        if (StringUtils.isNotBlank(orgid)) {
            organs.add(orgid);
            organs.addAll(organizationService.getChildrenById(orgid));
        } else {
            organs.addAll(admin ? Collections.emptyList() : organizationService.getSubordinateOrgIds(operator));
        }
        return organs;
    }

    @Override
    public Map<String, UserInfoDO> getUserProps(Set<String> names) {
        List<UserInfoDO> userInfoDO = userInfoMapper.selectBatchIds(names);
        Map<String, UserInfoDO> userInfoMap = Maps.newHashMap();
        if (CollectionUtils.isNotEmpty(userInfoDO)) {
            userInfoDO.forEach(item -> userInfoMap.put(item.getUserid(), item));
        }
        return userInfoMap;
    }

    @Override
    public List<String> getSuperiors(String uid, Integer rank) {
        return List.of();
    }

    /**
     * 查询所有人员信息 包含<角色 组织  扩展信息>不分页
     *
     * @param parameters 参数列表
     * @return 人员列表
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<MutableUser> getAllMutableUsersNoPage(Map<String, Object> parameters) {
        setUsernames(parameters);
        setUserFields(parameters);
        return userMapper.getAllMutableUsersNoPage(parameters);
    }

    @SuppressWarnings({"squid:S3252", "unchecked"})
    private void setUserFields(Map<String, Object> parameters) {
        if (parameters.get(USER_PERSONAL) != null) {
            String personal = parameters.get(USER_PERSONAL).toString();
            Map<String, String> tempMap = (Map<String, String>) JSONUtil.parse(personal);
            List<UserFields> fields = this.convertPersonBean(tempMap, null);
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
        List<UserFields> userFields = this.convertPersonBean(personal, username);
        userFieldsMapper.insert(userFields);
    }

    @Override
    public List<MutableUser> getAllMutableUsersByTenantId(String tenantId) {
        Assert.notNull(tenantId, "租户id不能为空");
        return userMapper.getAllMutableUsersByTenantId(tenantId);
    }

    @Override
    public void updateTenantUser(MutableUser source, LoginUser operator) {
        Assert.notNull(source, AuthorizeConstants.USER_NOT_FOUND);
        Assert.notNull(source.getUsername(), AuthorizeConstants.USER_NAME_NOT_EMPTY);
        String username = source.getUsername();
        MutableUser target = userMapper.getMutableUserById(username);
        Assert.notNull(target, AuthorizeConstants.USER_NOT_FOUND);
        BeanUtils.copyProperties(source, target);
        target.setNicknameAbbr(PinyinUtil.getPinyin(target.getNickname()));
        userMapper.updateMutableUser(target);
        userMapper.deleteUserRoles(username);
        addUserRoles(target, operator.getTenantId());
    }

    @Override
    public void exportMutableUsers(
            Page<MutableUser> pageable, Map<String, Object> parameters, HttpServletResponse response) {}
}
