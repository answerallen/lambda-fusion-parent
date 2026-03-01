package com.lambda.fusion.authority.model.user;

import com.google.common.collect.Sets;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Set;
import lombok.Data;

@Data
@SuppressFBWarnings("EI_EXPOSE_REP")
public class PopulateUserInfo {

    private Set<String> usernames = Sets.newHashSet();
    private Set<String> orgIds = Sets.newHashSet();
}
