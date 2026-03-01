package com.lambda.fusion.authority.service;

import com.lambda.fusion.authority.domain.authentication.NavigationQuery;
import com.lambda.fusion.authority.domain.resource.CreateResource;
import com.lambda.fusion.authority.domain.resource.MoveResource;
import com.lambda.fusion.authority.domain.resource.Resource;
import com.lambda.fusion.authority.domain.resource.ResourceTree;
import com.lambda.fusion.core.identity.LoginUserDetails;
import java.util.List;
import org.springframework.lang.NonNull;

public interface ResourceService {
    /**
     * 查询资源列表
     */
    List<Resource> getAllResources();

    /**
     * 获取系统资源
     */
    default List<ResourceTree> getChildren(NavigationQuery parameter) {
        return getChildren();
    }

    /**
     * 获取系统资源
     */
    List<ResourceTree> getChildren();

    /**
     * 获取系统资源
     *
     * @param id
     */
    List<ResourceTree> getChildren(String id);

    /**
     * 根据编号查询上级的资源信息
     *
     * @param id
     */
    List<Resource> getParentResources(String id);

    /**
     * 根据编号查询资源信息
     *
     * @param id
     */
    Resource getResourceById(String id);

    /**
     * 删除资源
     *
     * @param id 资源编号
     */
    void deleteResource(String id);

    /**
     * 创建新的资源
     *
     * @param resource 资源信息
     */
    Resource addResource(CreateResource resource);

    /**
     * 更新资源
     *
     * @param resource 资源信息
     */
    Resource updateResource(Resource resource);

    /**
     * 根据编号以递归方式获取所有的下级系统资源
     *
     * @param id      编号
     * @param results 结果集
     */
    void getAllChildren(String id, List<Resource> results);

    /**
     * 根据编号以递归方式获取所有的上级系统资源
     *
     * @param id      编号
     * @param results 结果集
     */
    void getAllParents(String id, List<Resource> results);

    /***
     * 以遍历所有资源的方式获取可用的下级节点
     *
     * @param target 指定节点
     */
    List<Resource> queryAvailableChildren(Resource target);

    /***
     * 获取用户拥有的所有下级(直接下级和间接下级)权限,主要用于授权
     * @param loginUserDetails  当前用户
     * @param resource    当前节点
     */
    List<Resource> getAllChildrenByOperator(@NonNull LoginUserDetails loginUserDetails, @NonNull Resource resource);

    /***
     * 获取用户拥有的所有上级(直接上级和间接下级)权限,主要用于授权
     * @param loginUserDetails
     * @param resource
     */
    List<Resource> getAllParentsByOperator(@NonNull LoginUserDetails loginUserDetails, @NonNull Resource resource);

    /***
     * 移动资源
     *
     * @param parameter 移动参数
     */
    void move(MoveResource parameter);
}
