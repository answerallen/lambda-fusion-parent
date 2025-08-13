package com.lambda.fusion.authority.client.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.authority.client.model.dto.ApiTokenInputDTO;
import com.lambda.fusion.authority.client.model.dto.ApiTokenPageQueryDTO;
import com.lambda.fusion.authority.client.model.entity.ApiTokenEntity;
import com.lambda.fusion.authority.client.service.ApiTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * Api Token授权信息 前端控制器
 * </p>
 *
 */
@Tag(name = "令牌管理")
@RestController
@RequestMapping("/api-token")
@RequiredArgsConstructor
public class ApiTokenController {
    private final ApiTokenService apiTokenService;

    @GetMapping({"/page","/page/{number:\\d+}", "/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(summary = "分页查询令牌", description = "分页查询令牌")
    public Page<ApiTokenEntity> page(@PathVariable(required = false) Integer number,
                                     @PathVariable(required = false) Integer size,
                                     @Valid ApiTokenPageQueryDTO apiTokenPageQueryDTO) {
        if (number != null) {
            apiTokenPageQueryDTO.setPageNum(number);
        }
        if (size != null) {
            apiTokenPageQueryDTO.setPageSize(size);
        }
        return apiTokenService.page(apiTokenPageQueryDTO.getPage(), apiTokenPageQueryDTO.getLambdaQueryWrapper());
    }

    @PostMapping
    @Operation(description = "新增AipToken", summary = "新增令牌")
    public void save(@RequestBody @Valid ApiTokenInputDTO tokenInputDTO) {
        ApiTokenEntity apiTokenEntity = tokenInputDTO.convertToEntity();
        apiTokenEntity.setApiToken(RandomStringUtils.secure().nextAlphabetic(32));
        apiTokenService.save(apiTokenEntity);
    }

    @DeleteMapping
    @Operation(description = "删除AipToken", summary = "删除令牌")
    public void delete(String id) {
        apiTokenService.removeById(id);
    }

    @PutMapping("/{id}")
    @Operation(description = "修改AipToken", summary = "修改令牌")
    public void update(@PathVariable("id") String id, @RequestBody @Valid ApiTokenInputDTO tokenInputDTO) {
        ApiTokenEntity apiTokenEntity = tokenInputDTO.convertToEntity();
        apiTokenEntity.setId(id);
        apiTokenService.updateById(apiTokenEntity);
    }
}
