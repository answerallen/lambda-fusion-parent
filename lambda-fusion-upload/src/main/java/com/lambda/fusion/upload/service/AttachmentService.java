package com.lambda.fusion.upload.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.upload.model.AttachmentGroupEntity;
import com.lambda.fusion.upload.model.AttachmentQuery;
import com.lambda.fusion.upload.model.AttachmentGroupView;
import com.lambda.fusion.upload.model.AttachmentView;
import com.lambda.fusion.upload.model.UpsertAttachmentGroup;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface AttachmentService {
    AttachmentView upload(MultipartFile file, String groupId, String clientName);

    void delete(String id);

    void deleteBatch(List<String> ids);

    AttachmentView getById(String id);

    String previewUrl(String id, Integer expirationSeconds);

    Page<AttachmentView> page(Integer pageNum, Integer pageSize, AttachmentQuery query);

    List<AttachmentGroupEntity> listGroups();

    AttachmentGroupEntity createGroup(UpsertAttachmentGroup group);

    AttachmentGroupEntity updateGroup(String id, UpsertAttachmentGroup group);

    void deleteGroup(String id);

    void changeGroup(String id, String groupId);

    void changeGroupBatch(List<String> ids, String groupId);

    List<String> listClientNames();

    List<AttachmentGroupView> listGroupViews();
}
