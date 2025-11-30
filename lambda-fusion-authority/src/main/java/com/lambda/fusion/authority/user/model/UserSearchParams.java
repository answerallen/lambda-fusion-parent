package com.lambda.fusion.authority.user.model;

import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 用户查询参数
 */
@Data
@Accessors(chain = true)
public class UserSearchParams {
    private List<String> usernames;
    private boolean dev;
    private boolean admin;
    private String uid;
    private String email;
    private String nickname;
    private String mobile;
    private String tenantId;
    private String authority;
    private List<UserFieldsEntity> personal;
    private Boolean isOnline;
    private Set<String> orgIds;
}
