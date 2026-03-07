package com.lambda.fusion.authority.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.fusion.authority.model.authentication.MenuQuery;
import com.lambda.fusion.authority.model.resource.Resource;
import com.lambda.fusion.authority.model.resource.ResourceEntity;
import com.lambda.fusion.authority.model.resource.ResourceTree;
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
@InterceptorIgnore(tenantLine = "true")
public interface ResourceMapper extends BaseMapper<ResourceEntity> {
    /**
     * 根据操作用户查询所有下级(直接下级和间接下级)的信息
     *
     * @param parameters
     * @return
     */
    List<Resource> getAllChildren(Map<String, Object> parameters);

    /**
     * 根据操作用户查询所有上级(直接上级和间接上级)的信息
     *
     * @param parameters
     * @return
     */
    List<Resource> getAllParents(Map<String, Object> parameters);

    /**
     * 查询直接下级资源,仅包含该节点下的直接下级
     *
     * @param id
     */
    List<ResourceTree> getDirectChildren(@Param("id") String id);

    /***
     * 查询所有下级资源(直接下级和间接下级)的资源信息
     * @param parentKeys
     */
    List<ResourceTree> queryAllChildren(@Param("parentKeys") String parentKeys);

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
     */
    default void updateResource(Resource resource) {
        ResourceEntity resourceEntity = ConvertUtils.convert(resource);
        this.updateById(resourceEntity);
    }

    /***
     * 根据编号查询资源信息
     *
     * @param id
     */
    default Resource getResourceById(String id) {
        ResourceEntity resourceEntity = selectById(id);
        return ConvertUtils.convert(resourceEntity);
    }

    /***
     * 新建资源
     *
     * @param resource
     * @return void
     */
    default void addResource(Resource resource) {
        ResourceEntity resourceEntity = ConvertUtils.convert(resource);
        this.insert(resourceEntity);
    }

    /***
     * 删除资源
     *
     * @param ids
     * @return void
     */
    default void deleteResource(Set<String> ids) {
        this.deleteByIds(ids);
    }

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
    default List<Resource> queryAvailableResources(MenuQuery parameter) {
        List<ResourceEntity> resourceEntities = selectList(new LambdaQueryWrapper<ResourceEntity>()
                .eq(ResourceEntity::getResMode, parameter.getMode())
                .ge(ResourceEntity::getResType, 0)
                .orderByAsc(ResourceEntity::getResLevel, ResourceEntity::getOrderNo));
        return ConvertUtils.convertList(resourceEntities);
    }

    /**
     * 查询系统资源列表
     *
     * @param
     */
    List<Resource> getAllResourcesByOrderNo();

    /***
     * 批量排序
     *
     * @param changed
     */
    void updateResourceOrdered(List<Resource> changed);

    /**
     * 批量更新隐藏/显示子资源
     *
     * @param changed 子资源
     * @param status  隐藏状态
     */
    void updateResourceIsHidden(@Param("list") List<ResourceTree> changed, @Param("status") boolean status);

    /***
     * 获取所有的资源信息
     */
    List<Resource> getAllResources();

    /***
     * 更新Parentkeys
     * @param resources
     */
    void updateResourceParentKeys(List<Resource> resources);

    /**
     * 是否已经执行过
     *
     * @param map
     * @return boolean
     */
    boolean hasChangedParentKeys(Map<String, Object> map);

    /***
     * 批量更新Rank值
     * @param changed2
     */
    void updateResourceLevel(List<Resource> changed2);

    /**
     * 更新移动的资源
     *
     * @param moved
     * @return void
     */
    void updateMovedResource(Resource moved);
}
