package com.lambda.fusion.permission.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@SuppressFBWarnings("EI_EXPOSE_REP")
public class PermissionPushRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String application;
    private String instanceId;
    private long pushedAt;
    private List<PermissionFileMetadata> files = new ArrayList<>();
}
