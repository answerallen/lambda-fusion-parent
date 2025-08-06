package com.lambda.fusion.auth.role.persistence;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.auth.role.bean.GroupDao;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface GroupMapper extends BaseMapper<GroupDao> {

    /**
     * 更新角色分组
     * @param oldGroupId 老分组ID
     * @param newGroupId 新分组ID
     * @return  条数
     */
    @Update("UPDATE M1_ROLES SET GROUP_ID = #{newGroupId} WHERE GROUP_ID = #{oldGroupId}")
    int updateRoleGroupId(String oldGroupId, String newGroupId);

    /**
     * 根据组织机构删除角色分组
     * @param organs
     */
    @Delete("<script>"  +
            "DELETE FROM M1_GROUPS WHERE GROUP_ID IN " +
            "<foreach collection='organs' open='(' item='id' separator=',' close=')'> #{id}</foreach>" +
            "</script>")
    @InterceptorIgnore(tenantLine = "true")
    void deleteByOrgIds(List<String> organs);

    /**
     * 获取角色分组信息
     * @param parameters    查询参数
     * @return  角色分组列表
     */
    @InterceptorIgnore(tenantLine = "true")
    List<GroupDao> getAllGroup(Map<String, Object> parameters);

    /**
     * 根据租户ID，删除分组
     * @param tenantIds 租户ID
     */
    void deleteGroupByTenantId(@Param("tenantIds") List<String> tenantIds);
}
