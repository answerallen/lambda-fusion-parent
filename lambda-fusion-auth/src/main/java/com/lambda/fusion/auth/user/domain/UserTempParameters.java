package com.lambda.fusion.auth.user.domain;

import com.google.common.collect.Sets;
import lombok.Data;

import java.util.Set;

@Data

public class UserTempParameters {

    private Set<String> uids = Sets.newHashSet();
    private Set<String> orgids = Sets.newHashSet();
}
