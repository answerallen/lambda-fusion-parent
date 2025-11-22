package com.lambda.fusion.authority.resource.service.impl;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.google.common.collect.Maps;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.fusion.authority.authentication.model.NavigationQuery;
import com.lambda.fusion.authority.resource.mapper.ResourceMapper;
import com.lambda.fusion.authority.resource.model.*;
import com.lambda.fusion.authority.resource.service.ResourceService;
import com.lambda.fusion.authority.role.service.RoleManager;
import com.lambda.fusion.core.Constants;
import com.lambda.fusion.core.tree.builder.TreeBuilder;
import com.lambda.fusion.core.tree.filter.TreeDataFilter;
import com.lambda.fusion.core.identity.Operator;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ResourceServiceImpl implements ResourceService {
    private final RoleManager roleManager;
    private final ResourceMapper resourceMapper;
    protected final TreeDataFilter treeDataFilter;

    @Override
    public List<Resource> getAllResources() {
        return resourceMapper.getAllResourcesByOrderNo();
    }

    @Override
    public List<ResourceTree> getChildren() {
        return getChildren(new NavigationQuery());
    }

    @Override
    public List<ResourceTree> getChildren(NavigationQuery parameter) {
        List<Resource> resources = resourceMapper.queryAvailableMutableResources(parameter);
        if (CollectionUtils.isEmpty(resources)) {
            return new ArrayList<>();
        }
        List<ResourceTree> list = new ArrayList<>(resources.size());
        resources.forEach(v -> {
            ResourceTree tree = new ResourceTree();
            BeanUtils.copyProperties(v, tree);
            list.add(tree);
        });
        final List<ResourceTree> resourceTreeList = treeDataFilter.filter(
                list,
                parameter.getName(),
                ResourceTree::getResName,
                ResourceTree::getId,
                ResourceTree::getParentKeys,
                target -> target.stream()
                        .sorted(Comparator.comparing(ResourceTree::getResLevel).thenComparing(ResourceTree::getOrderNo))
                        .collect(Collectors.toList()));
        return TreeBuilder.build(resourceTreeList);
    }

    @Override
    public List<ResourceTree> getChildren(String id) {
        List<ResourceTree> resourceTrees = resourceMapper.getDirectChildren(id);
        if (CollectionUtils.isNotEmpty(resourceTrees)) {
            for (ResourceTree resourceTree : resourceTrees) {
                List<ResourceTree> children = getChildren(resourceTree.getId());
                if (CollectionUtils.isNotEmpty(children)) {
                    resourceTree.setChildren(children);
                }
            }
        }
        return resourceTrees;
    }

    @Override
    public List<Resource> getParents(String id) {
        Resource resource = resourceMapper.getResourceById(id);
        if (resource != null) {
            List<Resource> list = new ArrayList<>();
            list.add(resource);
            if (resource.getParentId() != null) {
                List<Resource> parents = getParents(resource.getParentId());
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
    public Resource addResource(CreateResource parameter) {
        Resource resource = new Resource();
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
        Resource parent;
        String parentKeys = StringUtils.EMPTY;
        int rank = 0;
        if (StringUtils.isNotBlank(resource.getParentId())) {
            parent = getResourceById(resource.getParentId());
            Assert.notNull(parent, "lambda.authority.resource.parent.notfound");
            Assert.isFalse(
                    !parent.getResMode().equals(resource.getResMode()), "lambda.authority.resource.model.inconsistent");
            rank = parent.getResLevel() + 1;
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
        resource.setResLevel(rank);
        resource.setParentKeys(parentKeys);

        if (StringUtils.isBlank(resource.getResPath())) {
            resource.setResPath(null);
        }
        if (StringUtils.isBlank(resource.getIcon())) {
            resource.setIcon(null);
        }
        List<ResourceTree> children2 = resourceMapper.getDirectChildren(resource.getParentId());
        Objects.requireNonNull(children2);
        resource.setOrderNo(children2.size() + 1);
        resourceMapper.addResource(resource);
        List<CreateResource.ButtonParameter> buttons = parameter.getButtons();
        if (CollectionUtils.isNotEmpty(buttons)) {
            for (int i = 0; i < buttons.size(); i++) {
                Resource button = new Resource();
                BeanUtils.copyProperties(buttons.get(i), button);
                button.setId(UUID.fastUUID().toString());
                button.setParentId(resource.getId());
                button.setParentKeys(resource.getParentKeys() + Constants.SEPARATOR0 + resource.getId());
                button.setOrderNo(i + 1);
                button.setResType(ResourceType.BUTTON.ordinal());
                button.setResLevel(rank + 1);
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
        Resource resource = getResourceById(id);
        Assert.notNull(resource, "Resource not found");

        List<Resource> children = queryAvailableChildren(resource);
        children.addFirst(resource);
        Set<String> ids = children.stream().map(Resource::getId).collect(Collectors.toSet());
        resourceMapper.deleteResource(ids);
        resourceMapper.deleteRolesResource(ids);

        List<ResourceTree> children2 = resourceMapper.getDirectChildren(resource.getParentId());
        changeResourceOrdered(children2);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Resource updateResource(Resource resource) {
        Assert.notNull(resource, "Resource can't be null");
        Assert.notNull(resource.getId(), "Resource id can't be null");
        Resource source = getResourceById(resource.getId());
        // 更新时只更新属性，不改变上下级关系，因此parentKeys也无须变化
        resource.setResLevel(source.getResLevel());
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
                resource.setResLevel(Integer.MAX_VALUE);
            }
            String parentId = source.getParentId();
            Resource parent;
            if (StringUtils.isNotBlank(parentId)) {
                parent = getResourceById(parentId);
                Assert.notNull(parent, "lambda.authority.resource.parent.notfound");
            }
        }
        BeanUtils.copyProperties(resource, source);
        resourceMapper.updateResource(source);

        if (orderChanged) {
            List<ResourceTree> children2 = resourceMapper.getDirectChildren(source.getParentId());
            changeResourceOrdered(children2);
        }
        if (hiddenChanged) {
            List<ResourceTree> children3 = resourceMapper.getDirectChildren(source.getId());
            if (CollectionUtils.isNotEmpty(children3)) {
                resourceMapper.updateResourceIsHidden(children3, resource.isHidden());
            }
        }
        return source;
    }

    @Override
    public Resource getResourceById(String id) {
        Assert.notNull(id, "Resource id can't be null");
        return resourceMapper.getResourceById(id);
    }

    @Override
    public void getAllChildren(String id, List<Resource> results) {
        List<ResourceTree> children = resourceMapper.getDirectChildren(id);
        if (CollectionUtils.isNotEmpty(children)) {
            results.addAll(children);
            for (ResourceTree child : children) {
                getAllChildren(child.getId(), results);
            }
        }
    }

    @Override
    public void getAllParents(String id, List<Resource> results) {
        if (StringUtils.isBlank(id)) {
            return;
        }
        Resource resource = resourceMapper.getResourceById(id);
        if (resource != null) {
            results.addFirst(resource);
            if (StringUtils.isNotBlank(resource.getParentId())) {
                getAllParents(resource.getParentId(), results);
            }
        }
    }

    @Override
    public List<Resource> queryAvailableChildren(@NotNull Resource target) {
        Assert.notNull(target.getId(), "Resource id can't be null");
        String parentKeys = generateParentKeys(target.getParentKeys(), target.getId());
        List<ResourceTree> resourceTrees = resourceMapper.queryAllChildren(parentKeys);
        if (CollectionUtils.isNotEmpty(resourceTrees)) {
            int size = resourceTrees.size();
            List<Resource> list = new ArrayList<>(size);
            list.addAll(resourceTrees);
            return list;
        }
        return new ArrayList<>();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(@NotNull MoveResource parameter) {
        String id = parameter.getId();
        String tid = parameter.getTid();
        int type = parameter.getType();
        Assert.notNull(id, "Resource id can't be null");
        Assert.notNull(tid, "Resource id can't be null");
        Resource resource = resourceMapper.getResourceById(id);
        Resource target = resourceMapper.getResourceById(tid);
        String pid0 = resource.getParentId();
        String pid1 = target.getParentId();
        boolean peer = isPeer(pid0, pid1, type);

        // 需要改变排序号的资源列表
        List<Resource> changed = new ArrayList<>();
        // 需要改变parentKeys的资源列表
        List<Resource> changed2 = new ArrayList<>();
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
            List<ResourceTree> children = resourceMapper.getDirectChildren(pid0);
            changeResourceOrdered(children);
        }
    }

    private void handler2(
            Resource resource,
            Resource target,
            List<Resource> changed,
            List<Resource> changed2,
            String pid1,
            String parentKeys,
            boolean peer) {
        int n = 1;
        resource.setParentId(pid1);
        resource.setResLevel(target.getResLevel());
        List<ResourceTree> children = resourceMapper.getDirectChildren(pid1);
        for (Resource item : children) {
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
            List<ResourceTree> children2 = resourceMapper.queryAllChildren(parentKeys);
            String replacement = target.getParentKeys();
            parentKeysAndRankHandler(resource, target, replacement, children2, changed2);
        }
    }

    private void handler1(
            Resource resource,
            Resource target,
            List<Resource> changed,
            List<Resource> changed2,
            String pid1,
            String parentKeys,
            boolean peer) {
        int n = 1;
        resource.setParentId(pid1);
        resource.setResLevel(target.getResLevel());
        List<ResourceTree> children = resourceMapper.getDirectChildren(pid1);
        for (Resource item : children) {
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
            List<ResourceTree> children2 = resourceMapper.queryAllChildren(parentKeys);
            String replacement = target.getParentKeys();
            parentKeysAndRankHandler(resource, target, replacement, children2, changed2);
        }
    }

    private void handler0(
            Resource resource, Resource target, List<Resource> changed, List<Resource> changed2, String parentKeys) {
        resource.setParentId(target.getId());
        resource.setOrderNo(1);
        resource.setResLevel(target.getResLevel() + 1);
        List<ResourceTree> children = resourceMapper.getDirectChildren(target.getId());
        if (CollectionUtils.isNotEmpty(children)) {
            for (int i = 0; i < children.size(); i++) {
                children.get(i).setOrderNo(2 + i);
            }
            changed.addAll(children);
        }
        List<ResourceTree> children2 = resourceMapper.queryAllChildren(parentKeys);
        String replacement = generateParentKeys(target.getParentKeys(), target.getId());
        parentKeysAndRankHandler(resource, target, replacement, children2, changed2);
    }

    /***
     * 处理需要更新parentKeys属性的对象
     */
    private void parentKeysAndRankHandler(
            Resource resource,
            Resource target,
            String replacement,
            List<ResourceTree> children2,
            List<Resource> changed2) {
        log.trace("target：{}", target);
        if (CollectionUtils.isNotEmpty(children2)) {
            String searchString = resource.getParentKeys();
            String result;
            for (ResourceTree item : children2) {
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
                item.setResLevel(StringUtils.split(result, Constants.SEPARATOR0).length);
                changed2.add(item);
            }
        }
        resource.setParentKeys(replacement);
    }

    @Override
    public List<Resource> getAllChildrenByOperator(@NonNull Operator operator, @NonNull Resource resource) {
        String parentKeys = resource.getParentKeys();
        if (StringUtils.isNotBlank(parentKeys)) {
            parentKeys = resource.getParentKeys() + Constants.SEPARATOR0 + resource.getId();
        } else {
            parentKeys = resource.getId();
        }
        Map<String, Object> parameters = getParameters(operator, parentKeys);
        return resourceMapper.getAllChildren(parameters);
    }

    private Map<String, Object> getParameters(Operator operator, Object parentKeys) {
        Map<String, Object> parameters = Maps.newHashMap();
        parameters.put("parentKeys", parentKeys);
        if (!operator.isDev()) {
            Set<String> authorities = roleManager.getAuthoritiesByUser(operator.getUsername());
            authorities.add(operator.getUsername());
            parameters.put("authorities", authorities);
        }
        return parameters;
    }

    @Override
    public List<Resource> getAllParentsByOperator(@NonNull Operator operator, @NonNull Resource resource) {
        String parentKeys = resource.getParentKeys();
        if (StringUtils.isNotBlank(parentKeys)) {
            List<String> ids = Arrays.asList(parentKeys.split(Constants.SEPARATOR0));
            Map<String, Object> parameters = getParameters(operator, ids);
            return resourceMapper.getAllParents(parameters);
        }
        return new ArrayList<>();
    }

    /**
     * 改变资源列表顺序
     */
    private void changeResourceOrdered(List<ResourceTree> children2) {
        List<Resource> changed = new ArrayList<>(children2.size());
        int n = 1;
        for (Resource item : children2) {
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
     */
    private String generateParentKeys(String parentKeys, String id) {
        return StringUtils.isNotBlank(parentKeys) ? parentKeys + Constants.SEPARATOR0 + id : id;
    }
}
