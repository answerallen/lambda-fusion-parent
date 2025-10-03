package com.lambda.fusion.authority.user.model.bo;

import com.google.common.collect.Sets;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Set;
import lombok.Data;

@Data
@SuppressFBWarnings("EI_EXPOSE_REP")
public class UserTempParameters {

    private Set<String> uids = Sets.newHashSet();
    private Set<String> orgIds = Sets.newHashSet();
}
