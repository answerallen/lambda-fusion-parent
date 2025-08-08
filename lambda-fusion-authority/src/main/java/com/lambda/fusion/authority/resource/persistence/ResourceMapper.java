package com.lambda.fusion.authority.resource.persistence;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.lambda.fusion.authority.authorize.model.NavigationParameter;
import com.lambda.fusion.authority.resource.model.MutableResource;
import com.lambda.fusion.authority.resource.model.Resource;
import com.lambda.fusion.authority.resource.model.UserPermission;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 资源服务数据持久层接口
 *
 */
@Mapper
public interface ResourceMapper {
    /**
     * 根据操作用户查询所有下级(直接下级和间接下级)的信息
     *
     * @param parameters
     * @return
     */
    List<MutableResource> getAllChildren(Map<String, Object> parameters);

    /**
     * 根据操作用户查询所有上级(直接上级和间接上级)的信息
     *
     * @param parameters
     * @return
     */
    List<MutableResource> getAllParents(Map<String, Object> parameters);

    /**
     * 查询直接下级资源,仅包含该节点下的直接下级
     *
     * @param id
     */
    List<Resource> getDirectChildren(@Param("id") String id);

    /***
     * 查询所有下级资源(直接下级和间接下级)的资源信息
     * @param parentkeys
     */
    List<Resource> queryAllChildren(@Param("parentkeys") String parentkeys);

    /**
     * 是否包含下级资源
     *
     * @param id
     * @return
     */
    boolean hasChildren(String id);

    /**
     * 下级是否包含按钮
     *
     * @param pid
     * @param id
     */
    boolean hasButton(@Param("pid") String pid, @Param("id") String id);

    /**
     * 下级是否包含非按钮资源
     *
     * @param pid
     * @param id
     */
    boolean hasNotButton(@Param("pid") String pid, @Param("id") String id);

    /**
     * 修改资源
     *
     * @param resource
     */
    void updateResource(MutableResource resource);

    /***
     * 根据编号查询资源信息
     *
     * @param id
     */
    MutableResource getResourceById(String id);

    /***
     * 新建资源
     *
     * @param resource
     * @return void
     */
    void addResource(MutableResource resource);

    /***
     * 删除资源
     *
     * @param ids
     * @return void
     */
    void deleteResource(Set<String> ids);

    /***
     * 删除资源关系的角色列表
     *
     * @param ids
     * @return void
     */
    void deleteRolesResource(Set<String> ids);

    /***
     * 获取所有可用的资源
     * @param parameter 参数
     */
    List<MutableResource> queryAvailableMutableResources(NavigationParameter parameter);

    /**
     * 查询系统资源列表
     *
     * @param
     */
    List<MutableResource> getAllResourcesByOrderNo();

    /***
     * 批量排序
     *
     * @param changed
     */
    void updateResourceOrdered(List<MutableResource> changed);

    /**
     * 批量更新隐藏/显示子资源
     *
     * @param changed 子资源
     * @param status  隐藏状态
     */
    void updateResourceIsHidden(@Param("list") List<Resource> changed, @Param("status") boolean status);

    /***
     * 获取所有的资源信息
     */
    List<MutableResource> getAllMutableResources();

    /***
     * 更新Parentkeys
     * @param resources
     */
    void updateResourceParentkeys(List<MutableResource> resources);

    /**
     * 是否已经执行过
     *
     * @param map
     * @return boolean
     */
    boolean hasChangedParentkeys(Map<String, Object> map);

    /***
     * 批量更新Rank值
     * @param changed2
     */
    void updateResourceRank(List<MutableResource> changed2);

    /***
     * 根据资源API权限ID查询拥有者
     * @param permissionId
     */
    @InterceptorIgnore(tenantLine = "true")
    List<String> getUserIdsByResourcePermissionId(String permissionId);

    /**
     * 获取所有接口
     *
     */
    List<MutableResource> queryAvailableServices();

    /**
     * 更新移动的资源
     *
     * @param moved
     * @return void
     */
    void updateMovedResource(MutableResource moved);

    /**
     * 根据资源id删除所有接口关联关系
     *
     * @param resourceId
     */
    @InterceptorIgnore(tenantLine = "true")
    void deleteAllResourceApi(String resourceId);

    /**
     * 批量添加接口资源关联关系
     *
     * @param resourceId
     * @param ids
     */
    @InterceptorIgnore(tenantLine = "true")
    void batchInsertResourceApi(@Param("ids") Set<String> ids, @Param("resourceId") String resourceId);

    /**
     * 根据资源id获取关联的所有接口id
     *
     * @param resourceId
     * @return
     */
    @InterceptorIgnore(tenantLine = "true")
    List<String> getApiIdsByResourceId(String resourceId);

    /**
     * 获取权限关联了哪些用户
     *
     * @param permissionIds
     * @return
     */
    @InterceptorIgnore(tenantLine = "true")
    List<UserPermission> getUserPermissions(List<String> permissionIds);

    /**
     * 获取权限关联了哪些用户
     *
     * @param map Map
     * @return
     */
    @InterceptorIgnore(tenantLine = "true")
    List<UserPermission> getAllUserPermissions(Map<String, Object> map);
}
