package com.lambda.fusion.datascope.service;

import com.lambda.fusion.core.identity.UserDetails;
import java.util.List;

public interface DataScopeSmartService {
    void smartForCreated(int type, String objectId, String parentId, UserDetails operator);

    void smartForUpdated(int type, String objectId, String parentId, String previousParentId, UserDetails operator);

    void smartForDeleted(int type, String objectId);

    void smartForMoved(
            int type,
            String rootObjectId,
            String parentId,
            String previousParentId,
            List<String> cascadeObjectIds,
            UserDetails operator);
}
