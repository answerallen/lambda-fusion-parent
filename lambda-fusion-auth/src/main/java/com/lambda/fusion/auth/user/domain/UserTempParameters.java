package com.lambda.fusion.auth.user.domain;

import com.google.common.collect.Sets;
import java.util.Set;
import lombok.Data;

@Data
public class UserTempParameters {

    private Set<String> uids = Sets.newHashSet();
    private Set<String> orgids = Sets.newHashSet();
}
