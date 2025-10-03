package com.lambda.fusion.authority.user.model.dto;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Setter
@Getter
@Schema(description = "更新用户信息")
public class RestUserInfoDTO {

    @Hidden
    @Schema(description = "用户名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像文件")
    private MultipartFile files;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "用户新增字段")
    private String personal;
}
