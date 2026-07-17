package com.lambda.fusion.datascope.service.impl;

import com.lambda.fusion.core.identity.UserDetails;
import com.lambda.fusion.datascope.DataScopeConstants;
import com.lambda.fusion.datascope.mapper.DataScopeMapper;
import com.lambda.fusion.datascope.model.DataScopeEntity;
import com.lambda.fusion.datascope.service.DataScopeSmartService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@SuppressFBWarnings("EI_EXPOSE_REP2")
@RequiredArgsConstructor
public class DataScopeSmartServiceImpl implements DataScopeSmartService {

    private final DataScopeMapper dataScopeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void smartForCreated(int type, String objectId, String parentId, UserDetails operator) {
        if (StringUtils.isBlank(objectId)) {
            return;
        }
        Set<String> owners = new LinkedHashSet<>();
        if (operator != null && !operator.isAnyManager()) {
            owners.add(joinOwner(DataScopeConstants.USER, operator.getUsername()));
        }
        owners.addAll(loadCheckedOwners(type, parentId));
        if (owners.isEmpty()) {
            return;
        }
        List<DataScopeEntity> entities = new ArrayList<>(owners.size());
        for (String owner : owners) {
            String[] segments = splitOwner(owner);
            if (segments == null) {
                continue;
            }
            if (exists(type, objectId, segments[1], segments[0])) {
                continue;
            }
            DataScopeEntity entity = new DataScopeEntity();
            entity.setId(objectId);
            entity.setTid(segments[1]);
            entity.setTargetType(segments[0]);
            entity.setDomainType(type);
            entity.setChecked(1);
            entity.setRankLevel(0);
            entity.setTenantId(operator != null ? operator.getTenantId() : null);
            entities.add(entity);
        }
        if (CollectionUtils.isNotEmpty(entities)) {
            dataScopeMapper.batchInsert(entities);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void smartForUpdated(
            int type, String objectId, String parentId, String previousParentId, UserDetails operator) {
        if (StringUtils.isBlank(objectId) || StringUtils.equals(parentId, previousParentId)) {
            return;
        }
        Set<String> ownersSelf = loadCheckedOwners(type, objectId);
        Set<String> ownersOldParent = loadCheckedOwners(type, previousParentId);
        Set<String> ownersNewParent = loadCheckedOwners(type, parentId);
        applyOwnersDelta(type, objectId, ownersSelf, ownersOldParent, ownersNewParent, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void smartForDeleted(int type, String objectId) {
        if (StringUtils.isBlank(objectId)) {
            return;
        }
        dataScopeMapper.deleteByTypeAndId(type, objectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void smartForMoved(
            int type,
            String rootObjectId,
            String parentId,
            String previousParentId,
            List<String> cascadeObjectIds,
            UserDetails operator) {
        if (StringUtils.isBlank(rootObjectId) || CollectionUtils.isEmpty(cascadeObjectIds)) {
            return;
        }
        Set<String> ownersOldParent = loadCheckedOwners(type, previousParentId);
        Set<String> ownersNewParent = loadCheckedOwners(type, parentId);
        for (String objectId : cascadeObjectIds) {
            if (StringUtils.isBlank(objectId)) {
                continue;
            }
            Set<String> ownersSelf = loadCheckedOwners(type, objectId);
            applyOwnersDelta(type, objectId, ownersSelf, ownersOldParent, ownersNewParent, operator);
        }
    }

    private Set<String> loadCheckedOwners(int type, String resourceId) {
        if (StringUtils.isBlank(resourceId)) {
            return Collections.emptySet();
        }
        List<DataScopeEntity> records = dataScopeMapper.selectByTypeAndId(type, resourceId);
        if (CollectionUtils.isEmpty(records)) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (DataScopeEntity record : records) {
            if (!Objects.equals(record.getChecked(), 1)) {
                continue;
            }
            result.add(joinOwner(record.getTargetType(), record.getTid()));
        }
        return result;
    }

    private boolean exists(int type, String objectId, String targetId, String targetType) {
        List<DataScopeEntity> records = dataScopeMapper.selectByTypeAndId(type, objectId);
        if (CollectionUtils.isEmpty(records)) {
            return false;
        }
        for (DataScopeEntity record : records) {
            if (StringUtils.equals(record.getTid(), targetId)
                    && StringUtils.equals(record.getTargetType(), targetType)) {
                return true;
            }
        }
        return false;
    }

    private void applyOwnersDelta(
            int type,
            String objectId,
            Set<String> ownersSelf,
            Set<String> ownersOldParent,
            Set<String> ownersNewParent,
            UserDetails operator) {

        // 确保不会移除操作人本身的权限
        Set<String> safeOwnersOldParent = new LinkedHashSet<>(ownersOldParent);
        if (operator != null) {
            safeOwnersOldParent.remove(joinOwner(DataScopeConstants.USER, operator.getUsername()));
        }

        Set<String> removeOwners = new LinkedHashSet<>(ownersSelf);
        removeOwners.retainAll(safeOwnersOldParent);
        for (String owner : removeOwners) {
            String[] segments = splitOwner(owner);
            if (segments == null) {
                continue;
            }
            dataScopeMapper.deleteByTypeAndIdAndOwner(type, objectId, segments[1], segments[0]);
        }

        Set<String> addOwners = new LinkedHashSet<>(ownersNewParent);
        addOwners.removeAll(ownersSelf);
        if (CollectionUtils.isEmpty(addOwners)) {
            return;
        }
        List<DataScopeEntity> entities = new ArrayList<>(addOwners.size());
        for (String owner : addOwners) {
            String[] segments = splitOwner(owner);
            if (segments == null) {
                continue;
            }
            DataScopeEntity entity = new DataScopeEntity();
            entity.setId(objectId);
            entity.setDomainType(type);
            entity.setTid(segments[1]);
            entity.setTargetType(segments[0]);
            entity.setChecked(1);
            entity.setRankLevel(0);
            entity.setTenantId(operator != null ? operator.getTenantId() : null);
            entities.add(entity);
        }
        if (CollectionUtils.isNotEmpty(entities)) {
            dataScopeMapper.batchInsert(entities);
        }
    }

    private static String joinOwner(String targetType, String targetId) {
        return targetType + ":" + targetId;
    }

    private static String[] splitOwner(String owner) {
        if (StringUtils.isBlank(owner)) {
            return null;
        }
        int index = owner.indexOf(':');
        if (index < 1 || index >= owner.length() - 1) {
            return null;
        }
        return new String[] {owner.substring(0, index), owner.substring(index + 1)};
    }
}
