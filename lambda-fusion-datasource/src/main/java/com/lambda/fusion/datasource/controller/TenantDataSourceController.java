package com.lambda.fusion.datasource.controller;

import com.lambda.fusion.datasource.service.TenantDataSourceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据源管理")
@RestController
@RequestMapping("/tenant-datasource")
@RequiredArgsConstructor
public class TenantDataSourceController {

    private final TenantDataSourceService tenantDataSourceService;

}
