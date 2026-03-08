package com.lambda.fusion.upload.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.oss.client.OssClient;
import com.lambda.cloud.oss.manager.OssClientManager;
import com.lambda.cloud.oss.model.UploadObjectResult;
import com.lambda.fusion.upload.mapper.AttachmentGroupMapper;
import com.lambda.fusion.upload.mapper.AttachmentMapper;
import com.lambda.fusion.upload.model.AttachmentEntity;
import com.lambda.fusion.upload.model.AttachmentGroupEntity;
import com.lambda.fusion.upload.model.AttachmentQuery;
import com.lambda.fusion.upload.model.AttachmentView;
import com.lambda.fusion.upload.model.UpsertAttachmentGroup;
import com.lambda.fusion.upload.service.AttachmentService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {
    private static final int DEFAULT_EXPIRE_SECONDS = 3600;
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final AttachmentMapper attachmentMapper;
    private final AttachmentGroupMapper attachmentGroupMapper;
    private final OssClientManager ossClientManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttachmentView upload(MultipartFile file, String groupId, String clientName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        ensureGroupExists(groupId);
        String resolvedClientName = StringUtils.isBlank(clientName) ? "default" : clientName.trim();
        OssClient ossClient = ossClientManager.get(resolvedClientName);
        String fileName = normalizeFileName(file.getOriginalFilename());
        String objectKey = buildObjectKey(fileName);
        try {
            UploadObjectResult uploadObjectResult = ossClient.upload(
                    file.getInputStream(),
                    objectKey,
                    StringUtils.defaultIfBlank(file.getContentType(), DEFAULT_CONTENT_TYPE));
            AttachmentEntity entity = new AttachmentEntity();
            entity.setId(IdUtil.getSnowflakeNextIdStr());
            entity.setFileName(fileName);
            entity.setFileSize(file.getSize());
            entity.setContentType(StringUtils.defaultIfBlank(file.getContentType(), DEFAULT_CONTENT_TYPE));
            entity.setObjectKey(uploadObjectResult.getKey());
            entity.setFileUrl(uploadObjectResult.getUrl());
            entity.setGroupId(StringUtils.trimToNull(groupId));
            entity.setClientName(resolvedClientName);
            entity.setCreatedAt(LocalDateTime.now());
            attachmentMapper.insert(entity);
            return toView(entity, loadGroupNameMap());
        } catch (Exception e) {
            throw new IllegalStateException("附件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        AttachmentEntity entity = requireAttachment(id);
        try {
            ossClientManager.get(entity.getClientName()).delete(entity.getObjectKey());
        } catch (Exception ignored) {
        }
        attachmentMapper.deleteById(id);
    }

    @Override
    public AttachmentView getById(String id) {
        AttachmentEntity entity = requireAttachment(id);
        return toView(entity, loadGroupNameMap());
    }

    @Override
    public String previewUrl(String id, Integer expirationSeconds) {
        AttachmentEntity entity = requireAttachment(id);
        int expires = expirationSeconds == null || expirationSeconds <= 0 ? DEFAULT_EXPIRE_SECONDS : expirationSeconds;
        return ossClientManager.get(entity.getClientName()).getPrivateUrl(entity.getObjectKey(), expires);
    }

    @Override
    public Page<AttachmentView> page(Integer pageNum, Integer pageSize, AttachmentQuery query) {
        int current = pageNum == null || pageNum <= 0 ? 1 : pageNum;
        int size = pageSize == null || pageSize <= 0 ? 20 : pageSize;
        LambdaQueryWrapper<AttachmentEntity> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.isNotBlank(query.getFileName())) {
                wrapper.like(AttachmentEntity::getFileName, query.getFileName().trim());
            }
            if (StringUtils.isNotBlank(query.getGroupId())) {
                wrapper.eq(AttachmentEntity::getGroupId, query.getGroupId().trim());
            }
            if (StringUtils.isNotBlank(query.getClientName())) {
                wrapper.eq(AttachmentEntity::getClientName, query.getClientName().trim());
            }
        }
        wrapper.orderByDesc(AttachmentEntity::getCreatedAt);
        Page<AttachmentEntity> entityPage = attachmentMapper.selectPage(new Page<>(current, size), wrapper);
        Map<String, String> groupNameMap = loadGroupNameMap();
        Page<AttachmentView> result = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        result.setRecords(entityPage.getRecords().stream().map(v -> toView(v, groupNameMap)).toList());
        return result;
    }

    @Override
    public List<AttachmentGroupEntity> listGroups() {
        LambdaQueryWrapper<AttachmentGroupEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(AttachmentGroupEntity::getSortNo).orderByAsc(AttachmentGroupEntity::getCreatedAt);
        return attachmentGroupMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttachmentGroupEntity createGroup(UpsertAttachmentGroup group) {
        validateGroup(group);
        AttachmentGroupEntity entity = new AttachmentGroupEntity();
        entity.setId(IdUtil.getSnowflakeNextIdStr());
        entity.setGroupName(group.getGroupName().trim());
        entity.setGroupCode(StringUtils.trimToNull(group.getGroupCode()));
        entity.setSortNo(group.getSortNo() == null ? 0 : group.getSortNo());
        entity.setCreatedAt(LocalDateTime.now());
        attachmentGroupMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttachmentGroupEntity updateGroup(String id, UpsertAttachmentGroup group) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("分组编号不能为空");
        }
        validateGroup(group);
        AttachmentGroupEntity entity = attachmentGroupMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("附件分组不存在: " + id);
        }
        entity.setGroupName(group.getGroupName().trim());
        entity.setGroupCode(StringUtils.trimToNull(group.getGroupCode()));
        entity.setSortNo(group.getSortNo() == null ? 0 : group.getSortNo());
        attachmentGroupMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(String id) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("分组编号不能为空");
        }
        LambdaQueryWrapper<AttachmentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AttachmentEntity::getGroupId, id);
        Long count = attachmentMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new IllegalStateException("分组下存在附件，无法删除");
        }
        attachmentGroupMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeGroup(String id, String groupId) {
        AttachmentEntity entity = requireAttachment(id);
        ensureGroupExists(groupId);
        entity.setGroupId(StringUtils.trimToNull(groupId));
        attachmentMapper.updateById(entity);
    }

    private void ensureGroupExists(String groupId) {
        String normalized = StringUtils.trimToNull(groupId);
        if (normalized == null) {
            return;
        }
        AttachmentGroupEntity group = attachmentGroupMapper.selectById(normalized);
        if (group == null) {
            throw new IllegalArgumentException("附件分组不存在: " + normalized);
        }
    }

    private AttachmentEntity requireAttachment(String id) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("附件编号不能为空");
        }
        AttachmentEntity entity = attachmentMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("附件不存在: " + id);
        }
        return entity;
    }

    private String normalizeFileName(String originalFilename) {
        String normalized = StringUtils.defaultIfBlank(StringUtils.trimToNull(originalFilename), "unnamed");
        return normalized.replace("\\", "_").replace("/", "_");
    }

    private String buildObjectKey(String fileName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "attachments/" + datePath + "/" + IdUtil.fastSimpleUUID() + "-" + fileName;
    }

    private Map<String, String> loadGroupNameMap() {
        List<AttachmentGroupEntity> groups = listGroups();
        Map<String, String> map = new HashMap<>(groups.size());
        for (AttachmentGroupEntity group : groups) {
            if (group != null && StringUtils.isNotBlank(group.getId())) {
                map.put(group.getId(), group.getGroupName());
            }
        }
        return map;
    }

    private AttachmentView toView(AttachmentEntity entity, Map<String, String> groupNameMap) {
        AttachmentView view = new AttachmentView();
        view.setId(entity.getId());
        view.setFileName(entity.getFileName());
        view.setFileSize(entity.getFileSize());
        view.setContentType(entity.getContentType());
        view.setObjectKey(entity.getObjectKey());
        view.setFileUrl(entity.getFileUrl());
        view.setGroupId(entity.getGroupId());
        view.setClientName(entity.getClientName());
        view.setCreatedAt(entity.getCreatedAt());
        view.setGroupName(Objects.requireNonNullElse(groupNameMap.get(entity.getGroupId()), null));
        return view;
    }

    private void validateGroup(UpsertAttachmentGroup group) {
        if (group == null || StringUtils.isBlank(group.getGroupName())) {
            throw new IllegalArgumentException("分组名称不能为空");
        }
    }
}
