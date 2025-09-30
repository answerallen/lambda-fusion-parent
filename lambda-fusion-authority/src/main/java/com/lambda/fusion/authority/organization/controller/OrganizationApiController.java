package com.lambda.fusion.authority.organization.controller;

import com.lambda.fusion.authority.organization.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/authority/organ/api")
@Tag(name = "组织管理")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class OrganizationApiController {

    private final OrganizationService organizationService;

    @GetMapping("/{orgid}")
    @Operation(summary = "根据组织机构ID获取父节点", description = "根据组织机构ID获取父节点")
    public List<String> getparentKeysById(@PathVariable String orgid) {
        return organizationService.getParentsById(orgid);
    }
}
