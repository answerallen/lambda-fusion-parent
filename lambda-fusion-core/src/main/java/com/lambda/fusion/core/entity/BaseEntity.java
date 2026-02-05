package com.lambda.fusion.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = -2694074995776393985L;

    /**
     * 创建用户
     * <p>
     * 记录创建该记录的用户标识，在插入数据时自动填充。
     * 通常存储用户ID或用户名，用于审计追踪。
     * </p>
     */
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    /**
     * 创建时间
     * <p>
     * 记录该记录的创建时间，在插入数据时自动填充当前时间。
     * 使用LocalDateTime类型，避免时区问题。
     * </p>
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createAt;

    /**
     * 更新用户
     * <p>
     * 记录最后更新该记录的用户标识，在更新数据时自动填充。
     * 通常存储用户ID或用户名，用于审计追踪。
     * </p>
     */
    @TableField(fill = FieldFill.UPDATE)
    private String updatedBy;

    /**
     * 更新时间
     * <p>
     * 记录该记录的最后更新时间，在更新数据时自动填充当前时间。
     * 使用LocalDateTime类型，避免时区问题。
     * </p>
     */
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updatedAt;
}
