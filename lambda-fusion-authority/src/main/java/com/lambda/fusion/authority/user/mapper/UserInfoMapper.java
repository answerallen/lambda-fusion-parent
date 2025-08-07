package com.lambda.fusion.authority.user.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.user.domain.UserInfoDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfoDO> {

    /**
     * 获取用户附加信息
     * @param id 用户id
     * @return 用户附加信息
     */
    @InterceptorIgnore(tenantLine = "1")
    UserInfoDO getProps(String id);

    /**
     * 重置或修改密码后修改updatePwd
     * @param userName 用户id
     * @param updatePwd 是否需要更改密码:1为是,0为否
     * @return 返回影响行数
     */
    @InterceptorIgnore(tenantLine = "1")
    Integer updateStatus(String userName, Boolean updatePwd);

    /**
     * 更改用户头像信息
     * @param userName  用户名
     * @param avatar    头像
     * @return  返回影响行数
     */
    Integer updateAvatar(String userName, String avatar);
}
