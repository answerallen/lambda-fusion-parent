package com.lambda.fusion.permission.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@SuppressFBWarnings("EI_EXPOSE_REP")
public class PermissionFileMetadata implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String version = "1.0.0";
    private String generatedAt;
    private String module;
    private String basePackage;
    private int totalApis;
    private List<ApiPermissionMetadata> apis = new ArrayList<>();
}
