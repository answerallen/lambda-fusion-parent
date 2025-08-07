package com.lambda.fusion.authority.client.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.authority.client.domain.dto.ApiTokenDO;
import com.lambda.fusion.authority.client.domain.vo.SaveTokenVO;
import com.lambda.fusion.authority.client.domain.vo.UpdateTokenVO;
import com.lambda.fusion.authority.client.service.ApiTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * Api Token授权信息 前端控制器
 * </p>
 *
 */
@Tag(name = "令牌管理")
@RestController
@RequestMapping("/api-token")
public class ApiTokenController {

    @Resource
    private ApiTokenService apiTokenService;

    @GetMapping(value = {"/pages/{number:\\d+}", "/pages/{number:\\d+}/size/{size:\\d+}"})
    @Operation(
            summary = "分页查询令牌",
            description = "分页查询令牌",
            parameters = {
                @Parameter(
                        name = "number",
                        description = "当前页码",
                        in = ParameterIn.PATH,
                        schema = @Schema(defaultValue = "1")),
                @Parameter(
                        name = "size",
                        description = "每页条数",
                        in = ParameterIn.PATH,
                        schema = @Schema(defaultValue = "20"))
            })
    public Page<ApiTokenDO> page(@PathVariable("number") int number, @PathVariable("size") int size) {
        return apiTokenService.page(new Page<>(number, size));
    }

    @PostMapping
    @Operation(description = "新增AipToken", summary = "新增令牌")
    public ApiTokenDO save(@RequestBody @Valid SaveTokenVO saveDesignPageVo) {
        final ApiTokenDO apiTokenDO = new ApiTokenDO();
        BeanUtils.copyProperties(saveDesignPageVo, apiTokenDO);
        apiTokenDO.setApiToken(RandomStringUtils.randomAlphanumeric(32));
        apiTokenService.save(apiTokenDO);
        return apiTokenService.getById(apiTokenDO.getId());
    }

    @DeleteMapping
    @Operation(description = "删除AipToken", summary = "删除令牌")
    public void delete(String id) {
        apiTokenService.removeById(id);
    }

    @PutMapping
    @Operation(description = "修改AipToken", summary = "修改令牌")
    public ApiTokenDO update(@RequestBody @Valid UpdateTokenVO updateTokenVO) {
        final ApiTokenDO apiTokenDO = new ApiTokenDO();
        BeanUtils.copyProperties(updateTokenVO, apiTokenDO);
        apiTokenService.updateById(apiTokenDO);
        return apiTokenService.getById(apiTokenDO.getId());
    }
}
