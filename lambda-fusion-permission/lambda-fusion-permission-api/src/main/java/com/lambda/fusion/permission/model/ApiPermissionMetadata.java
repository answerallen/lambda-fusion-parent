package com.lambda.fusion.permission.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@SuppressFBWarnings("EI_EXPOSE_REP")
public class ApiPermissionMetadata implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String path;
    private String method;
    private List<String> permissions = new ArrayList<>();
    private String description;
    private String group;
    private String controller;
    private String methodName;
    private boolean deprecated = false;
    private List<String> tags = new ArrayList<>();
    private String application;
    private String module;
}
