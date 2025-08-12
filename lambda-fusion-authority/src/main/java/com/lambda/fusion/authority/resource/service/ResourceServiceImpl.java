package com.lambda.fusion.authority.resource.service;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.google.common.collect.Maps;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.fusion.authority.authentication.model.NavigationQuery;
import com.lambda.fusion.authority.resource.mapper.ResourceMapper;
import com.lambda.fusion.authority.resource.model.*;
import com.lambda.fusion.authority.role.service.RoleManager;
import com.lambda.fusion.core.Constants;
import com.lambda.fusion.core.tree.ITreeDataFilter;
import com.lambda.fusion.core.tree.TreeFactory;
import com.lambda.fusion.core.user.User;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ResourceServiceImpl implements ResourceService {
    private final RoleManager roleManager;
    private final ResourceMapper resourceMapper;
    protected final ITreeDataFilter treeDataFilter;

    @Override
    public List<MutableResource> getAllResources() {
        return resourceMapper.getAllResourcesByOrderNo();
    }

    @Override
    public List<Resource> getChildren() {
        return getChildren(new NavigationQuery());
    }

    @Override
    public List<Resource> getChildren(NavigationQuery parameter) {
        List<MutableResource> resources = resourceMapper.queryAvailableMutableResources(parameter);
        if (CollectionUtils.isEmpty(resources)) {
            return new ArrayList<>();
        }
        List<Resource> list = new ArrayList<>(resources.size());
        resources.forEach(v -> {
            Resource tree = new Resource();
            BeanUtils.copyProperties(v, tree);
            list.add(tree);
        });
        final List<Resource> resourceList = treeDataFilter.filter(
                list,
                parameter.getName(),
                Resource::getResName,
                Resource::getId,
                Resource::getParentKeys,
                target -> target.stream()
                        .sorted(Comparator.comparing(Resource::getResRank).thenComparing(Resource::getOrderNo))
                        .collect(Collectors.toList()));
        return TreeFactory.build(resourceList);
    }

    @Override
    public List<Resource> getChildren(String id) {
        List<Resource> resources = resourceMapper.getDirectChildren(id);
        if (CollectionUtils.isNotEmpty(resources)) {
            for (Resource resource : resources) {
                List<Resource> children = getChildren(resource.getId());
                if (CollectionUtils.isNotEmpty(children)) {
                    resource.setChildren(children);
                }
            }
        }
        return resources;
    }

    @Override
    public List<MutableResource> getParents(String id) {
        MutableResource resource = resourceMapper.getResourceById(id);
        if (resource != null) {
            List<MutableResource> list = new ArrayList<>();
            list.add(resource);
            if (resource.getParentId() != null) {
                List<MutableResource> parents = getParents(resource.getParentId());
                if (parents != null) {
                    list.addAll(parents);
                }
            }
            return list;
        }
        return new ArrayList<>();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MutableResource addResource(ResourceParameter parameter) {
        MutableResource resource = new MutableResource();
        int type = parameter.getResType();
        String id = UUID.fastUUID().toString();
        if (type == 0) {
            if (StringUtils.isNotBlank(parameter.getMethod())) {
                id = parameter.getMethod();
            } else {
                id = IdWorker.getIdStr();
            }
        }
        resource.setId(id);
        BeanUtils.copyProperties(parameter, resource);
        MutableResource parent;
        String parentKeys = StringUtils.EMPTY;
        int rank = 0;
        if (StringUtils.isNotBlank(resource.getParentId())) {
            parent = getResourceById(resource.getParentId());
            Assert.notNull(parent, "lambda.authority.resource.parent.notfound");
            Assert.isFalse(
                    !parent.getResMode().equals(resource.getResMode()), "lambda.authority.resource.model.inconsistent");
            rank = parent.getResRank() + 1;
            parentKeys = parent.getParentKeys();
            if (StringUtils.isNotBlank(parentKeys)) {
                parentKeys += (Constants.SEPARATOR0 + parent.getId());
            } else {
                parentKeys = parent.getId();
            }
        }
        if (resource.getResType() == ResourceType.BUTTON.ordinal()) {
            rank = Integer.MAX_VALUE;
        }
        resource.setResRank(rank);
        resource.setParentKeys(parentKeys);

        if (StringUtils.isBlank(resource.getResPath())) {
            resource.setResPath(null);
        }
        if (StringUtils.isBlank(resource.getIco())) {
            resource.setIco(null);
        }
        List<Resource> children2 = resourceMapper.getDirectChildren(resource.getParentId());
        Objects.requireNonNull(children2);
        resource.setOrderNo(children2.size() + 1);
        resourceMapper.addResource(resource);
        List<ResourceParameter.ButtonParameter> buttons = parameter.getButtons();
        if (CollectionUtils.isNotEmpty(buttons)) {
            for (int i = 0; i < buttons.size(); i++) {
                MutableResource button = new MutableResource();
                BeanUtils.copyProperties(buttons.get(i), button);
                button.setId(UUID.fastUUID().toString());
                button.setParentId(resource.getId());
                button.setParentKeys(resource.getParentKeys() + Constants.SEPARATOR0 + resource.getId());
                button.setOrderNo(i + 1);
                button.setResType(ResourceType.BUTTON.ordinal());
                button.setResRank(rank + 1);
                resourceMapper.addResource(button);
            }
        }

        changeResourceOrdered(children2);
        return resourceMapper.getResourceById(resource.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteResource(String id) {
        Assert.notNull(id, "Resource id can't be null");
        MutableResource resource = getResourceById(id);
        Assert.notNull(resource, "Resource not found");

        List<MutableResource> children = queryAvailableChildren(resource);
        children.add(0, resource);
        Set<String> ids = children.stream().map(MutableResource::getId).collect(Collectors.toSet());
        resourceMapper.deleteResource(ids);
        resourceMapper.deleteRolesResource(ids);

        List<Resource> children2 = resourceMapper.getDirectChildren(resource.getParentId());
        changeResourceOrdered(children2);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MutableResource updateResource(MutableResource resource) {
        Assert.notNull(resource, "Resource can't be null");
        Assert.notNull(resource.getId(), "Resource id can't be null");
        MutableResource source = getResourceById(resource.getId());
        // 更新时只更新属性，不改变上下级关系，因此parentKeys也无须变化
        resource.setResRank(source.getResRank());
        resource.setParentKeys(source.getParentKeys());
        Assert.notNull(source, "resource not found");
        // 更新时如果没有传顺序号,则不修改顺序值
        if (resource.getOrderNo() == 0) {
            resource.setOrderNo(source.getOrderNo());
        }
        boolean orderChanged = source.getOrderNo() != resource.getOrderNo();
        boolean typeChanged = !source.getResType().equals(resource.getResType());
        boolean hiddenChanged = source.isHidden() != resource.isHidden();
        if (typeChanged) {
            if (resource.getResType() == ResourceType.BUTTON.ordinal()) {
                resource.setResRank(Integer.MAX_VALUE);
            }
            String parentId = source.getParentId();
            MutableResource parent;
            if (StringUtils.isNotBlank(parentId)) {
                parent = getResourceById(parentId);
                Assert.notNull(parent, "lambda.authority.resource.parent.notfound");
            }
        }
        BeanUtils.copyProperties(resource, source);
        resourceMapper.updateResource(source);

        if (orderChanged) {
            List<Resource> children2 = resourceMapper.getDirectChildren(source.getParentId());
            changeResourceOrdered(children2);
        }
        if (hiddenChanged) {
            List<Resource> children3 = resourceMapper.getDirectChildren(source.getId());
            if (CollectionUtils.isNotEmpty(children3)) {
                resourceMapper.updateResourceIsHidden(children3, resource.isHidden());
            }
        }
        return source;
    }

    @Override
    public MutableResource getResourceById(String id) {
        Assert.notNull(id, "Resource id can't be null");
        return resourceMapper.getResourceById(id);
    }

    @Override
    public void getAllChildren(String id, List<MutableResource> results) {
        List<Resource> children = resourceMapper.getDirectChildren(id);
        if (CollectionUtils.isNotEmpty(children)) {
            results.addAll(children);
            for (Resource child : children) {
                getAllChildren(child.getId(), results);
            }
        }
    }

    @Override
    public void getAllParents(String id, List<MutableResource> results) {
        if (StringUtils.isBlank(id)) {
            return;
        }
        MutableResource resource = resourceMapper.getResourceById(id);
        if (resource != null) {
            results.add(0, resource);
            if (StringUtils.isNotBlank(resource.getParentId())) {
                getAllParents(resource.getParentId(), results);
            }
        }
    }

    @Override
    public List<MutableResource> queryAvailableChildren(@NotNull MutableResource target) {
        Assert.notNull(target.getId(), "Resource id can't be null");
        String parentKeys = generateParentKeys(target.getParentKeys(), target.getId());
        List<Resource> resources = resourceMapper.queryAllChildren(parentKeys);
        if (CollectionUtils.isNotEmpty(resources)) {
            int size = resources.size();
            List<MutableResource> list = new ArrayList<>(size);
            list.addAll(resources);
            return list;
        }
        return new ArrayList<>();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(@NotNull MoveParameter parameter) {
        String id = parameter.getId();
        String tid = parameter.getTid();
        int type = parameter.getType();
        Assert.notNull(id, "Resource id can't be null");
        Assert.notNull(tid, "Resource id can't be null");
        MutableResource resource = resourceMapper.getResourceById(id);
        MutableResource target = resourceMapper.getResourceById(tid);
        String pid0 = resource.getParentId();
        String pid1 = target.getParentId();
        boolean peer = isPeer(pid0, pid1, type);

        // 需要改变排序号的资源列表
        List<MutableResource> changed = new ArrayList<>();
        // 需要改变parentKeys的资源列表
        List<MutableResource> changed2 = new ArrayList<>();
        String parentKeys = generateParentKeys(resource.getParentKeys(), resource.getId());
        switch (parameter.getType()) {
            case 0:
                handler0(resource, target, changed, changed2, parentKeys);
                break;
            case 1:
                handler1(resource, target, changed, changed2, pid1, parentKeys, peer);
                break;
            case 2:
                handler2(resource, target, changed, changed2, pid1, parentKeys, peer);
                break;
            default:
                break;
        }
        resourceMapper.updateMovedResource(resource);
        if (CollectionUtils.isNotEmpty(changed)) {
            resourceMapper.updateResourceOrdered(changed);
        }
        if (CollectionUtils.isNotEmpty(changed2)) {
            resourceMapper.updateResourceParentkeys(changed2);
            resourceMapper.updateResourceRank(changed2);
        }
        if (!peer) {
            List<Resource> children = resourceMapper.getDirectChildren(pid0);
            changeResourceOrdered(children);
        }
    }

    private void handler2(
            MutableResource resource,
            MutableResource target,
            List<MutableResource> changed,
            List<MutableResource> changed2,
            String pid1,
            String parentKeys,
            boolean peer) {
        int n = 1;
        resource.setParentId(pid1);
        resource.setResRank(target.getResRank());
        List<Resource> children = resourceMapper.getDirectChildren(pid1);
        for (MutableResource item : children) {
            item.setOrderNo(n);
            if (item.getId().equals(target.getId())) {
                resource.setOrderNo(n + 1);
                n++;
            }
            if (!item.getId().equals(resource.getId())) {
                changed.add(item);
                n++;
            }
        }
        if (!peer) {
            List<Resource> children2 = resourceMapper.queryAllChildren(parentKeys);
            String replacement = target.getParentKeys();
            parentKeysAndRankHandler(resource, target, replacement, children2, changed2);
        }
    }

    private void handler1(
            MutableResource resource,
            MutableResource target,
            List<MutableResource> changed,
            List<MutableResource> changed2,
            String pid1,
            String parentKeys,
            boolean peer) {
        int n = 1;
        resource.setParentId(pid1);
        resource.setResRank(target.getResRank());
        List<Resource> children = resourceMapper.getDirectChildren(pid1);
        for (MutableResource item : children) {
            if (item.getId().equals(target.getId())) {
                resource.setOrderNo(n);
                n++;
            }
            if (!item.getId().equals(resource.getId())) {
                item.setOrderNo(n);
                changed.add(item);
                n++;
            }
        }
        if (!peer) {
            List<Resource> children2 = resourceMapper.queryAllChildren(parentKeys);
            String replacement = target.getParentKeys();
            parentKeysAndRankHandler(resource, target, replacement, children2, changed2);
        }
    }

    private void handler0(
            MutableResource resource,
            MutableResource target,
            List<MutableResource> changed,
            List<MutableResource> changed2,
            String parentKeys) {
        resource.setParentId(target.getId());
        resource.setOrderNo(1);
        resource.setResRank(target.getResRank() + 1);
        List<Resource> children = resourceMapper.getDirectChildren(target.getId());
        if (CollectionUtils.isNotEmpty(children)) {
            for (int i = 0; i < children.size(); i++) {
                children.get(i).setOrderNo(2 + i);
            }
            changed.addAll(children);
        }
        List<Resource> children2 = resourceMapper.queryAllChildren(parentKeys);
        String replacement = generateParentKeys(target.getParentKeys(), target.getId());
        parentKeysAndRankHandler(resource, target, replacement, children2, changed2);
    }

    /***
     * 处理需要更新parentKeys属性的对象
     */
    private void parentKeysAndRankHandler(
            MutableResource resource,
            MutableResource target,
            String replacement,
            List<Resource> children2,
            List<MutableResource> changed2) {
        log.trace("target：{}", target);
        if (CollectionUtils.isNotEmpty(children2)) {
            String searchString = resource.getParentKeys();
            String result;
            for (Resource item : children2) {
                if (StringUtils.isNotBlank(searchString)) {
                    if (StringUtils.isNotBlank(replacement)) {
                        result = StringUtils.replace(item.getParentKeys(), searchString, replacement);
                    } else {
                        result = StringUtils.removeStart(item.getParentKeys(), searchString + Constants.SEPARATOR0);
                    }
                } else if (StringUtils.isNotBlank(replacement)) {
                    result = replacement + Constants.SEPARATOR0 + item.getParentKeys();
                } else {
                    result = item.getParentKeys();
                }
                item.setParentKeys(result);
                item.setResRank(StringUtils.split(result, Constants.SEPARATOR0).length);
                changed2.add(item);
            }
        }
        resource.setParentKeys(replacement);
    }

    @Override
    public List<MutableResource> getAllChildrenByOperator(
            @NonNull User operator, @NonNull MutableResource resource) {
        String parentKeys = resource.getParentKeys();
        if (StringUtils.isNotBlank(parentKeys)) {
            parentKeys = resource.getParentKeys() + Constants.SEPARATOR0 + resource.getId();
        } else {
            parentKeys = resource.getId();
        }
        Map<String, Object> parameters = Maps.newHashMap();
        parameters.put("parentKeys", parentKeys);
        if (!operator.isDev()) {
            Set<String> authorities = roleManager.getAuthoritiesByUser(operator.getUsername());
            authorities.add(operator.getUsername());
            parameters.put("authorities", authorities);
        }
        return resourceMapper.getAllChildren(parameters);
    }

    @Override
    public List<MutableResource> getAllParentsByOperator(
            @NonNull User operator, @NonNull MutableResource resource) {
        String parentKeys = resource.getParentKeys();
        if (StringUtils.isNotBlank(parentKeys)) {
            List<String> ids = Arrays.asList(parentKeys.split(Constants.SEPARATOR0));
            Map<String, Object> parameters = Maps.newHashMap();
            parameters.put("parentKeys", ids);
            if (!operator.isDev()) {
                Set<String> authorities = roleManager.getAuthoritiesByUser(operator.getUsername());
                authorities.add(operator.getUsername());
                parameters.put("authorities", authorities);
            }
            return resourceMapper.getAllParents(parameters);
        }
        return new ArrayList<>();
    }

    /**
     * 改变资源列表顺序
     */
    private void changeResourceOrdered(List<Resource> children2) {
        List<MutableResource> changed = new ArrayList<>(children2.size());
        int n = 1;
        for (MutableResource item : children2) {
            if (item.getOrderNo() != n) {
                item.setOrderNo(n);
                changed.add(item);
            }
            n++;
        }
        if (CollectionUtils.isNotEmpty(changed)) {
            resourceMapper.updateResourceOrdered(changed);
        }
    }

    /***
     * 是否要移动的对象与目标对象是否为同级别
     *
     * @param pid0
     * @param pid1
     * @param type 0:下级，1:之前，2:之后
     * @return boolean
     */
    private boolean isPeer(String pid0, String pid1, int type) {
        if (type == 0) {
            return false;
        }
        if (StringUtils.isBlank(pid0) && StringUtils.isBlank(pid1)) {
            return true;
        } else if (StringUtils.isNotBlank(pid0) && StringUtils.isBlank(pid1)) {
            return false;
        } else if (StringUtils.isNotBlank(pid1) && StringUtils.isBlank(pid0)) {
            return false;
        } else {
            return pid0.equals(pid1);
        }
    }

    /***
     * 生成parentKeys
     * @param parentKeys
     * @param id
     * @return java.lang.String
     */
    private String generateParentKeys(String parentKeys, String id) {
        return StringUtils.isNotBlank(parentKeys) ? parentKeys + Constants.SEPARATOR0 + id : id;
    }
}
