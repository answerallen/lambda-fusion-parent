package com.lambda.fusion.authority.resource.service;

import com.lambda.fusion.authority.resource.model.Resource;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Set;
import java.util.function.Predicate;

@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ChildrenFilter implements Predicate<Resource> {

    private final Resource target;

    private final Set<String> children;

    public ChildrenFilter(Resource target, Set<String> children) {
        this.target = target;
        this.children = children;
    }

    @Override
    public boolean test(Resource resource) {
        int level0 = target.getResRank();
        String id = target.getId();
        int level1 = resource.getResRank();
        String pid = resource.getParentId();
        if (level1 > level0) {
            int disparity = level1 - level0;
            boolean c1 = disparity == 1 && id.equals(pid);
            boolean c2 = disparity > 1 && children.contains(pid);
            if (c1 || c2) {
                children.add(resource.getId());
                return true;
            }
        }
        return false;
    }
}
