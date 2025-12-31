package com.lambda.fusion.datasource.model;

import com.lambda.fusion.datasource.api.DataSourceChangeCallback;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SubscriberInfo {
    private String tenantId;
    private DataSourceChangeCallback callback;
}
