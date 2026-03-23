package com.lambda.fusion.datascope.commons.event;

import com.lambda.fusion.core.identity.UserDetails;
import com.lambda.fusion.datascope.DataScopeConstants;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
@SuppressFBWarnings("EI_EXPOSE_REP")
public class DataScopeObjectChangedEvent extends ApplicationEvent {

    private final DataScopeConstants.ChangeType changeType;
    private final String businessKey;
    private final String objectId;
    private final String parentId;
    private final String previousParentId;
    private final List<String> cascadeObjectIds;
    private final UserDetails operator;

    private DataScopeObjectChangedEvent(
            Object source,
            DataScopeConstants.ChangeType changeType,
            String businessKey,
            String objectId,
            String parentId,
            String previousParentId,
            List<String> cascadeObjectIds,
            UserDetails operator) {
        super(source);
        this.changeType = changeType;
        this.businessKey = businessKey;
        this.objectId = objectId;
        this.parentId = parentId;
        this.previousParentId = previousParentId;
        this.cascadeObjectIds = cascadeObjectIds;
        this.operator = operator;
    }

    public static DataScopeObjectChangedEvent created(
            Object source, String businessKey, String objectId, String parentId, UserDetails operator) {
        return new DataScopeObjectChangedEvent(
                source, DataScopeConstants.ChangeType.CREATED, businessKey, objectId, parentId, null, null, operator);
    }

    public static DataScopeObjectChangedEvent updated(
            Object source,
            String businessKey,
            String objectId,
            String parentId,
            String previousParentId,
            UserDetails operator) {
        return new DataScopeObjectChangedEvent(
                source,
                DataScopeConstants.ChangeType.UPDATED,
                businessKey,
                objectId,
                parentId,
                previousParentId,
                null,
                operator);
    }

    public static DataScopeObjectChangedEvent moved(
            Object source,
            String businessKey,
            String objectId,
            String parentId,
            String previousParentId,
            List<String> cascadeObjectIds,
            UserDetails operator) {
        return new DataScopeObjectChangedEvent(
                source,
                DataScopeConstants.ChangeType.MOVED,
                businessKey,
                objectId,
                parentId,
                previousParentId,
                cascadeObjectIds,
                operator);
    }

    public static DataScopeObjectChangedEvent deleted(
            Object source, String businessKey, String objectId, UserDetails operator) {
        return new DataScopeObjectChangedEvent(
                source, DataScopeConstants.ChangeType.DELETED, businessKey, objectId, null, null, null, operator);
    }
}
