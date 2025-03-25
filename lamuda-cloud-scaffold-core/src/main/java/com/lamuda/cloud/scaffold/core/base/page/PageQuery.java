package com.lamuda.cloud.scaffold.core.base.page;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class PageQuery<T> {
    @NotNull(message = "pageNum不能为空")
    private Integer pageNum = 1;
    @NotNull(message = "pageSize不能为空")
    private Integer pageSize = Integer.MAX_VALUE;

    public Page<T> getPage() {
        return new PageDTO<>(pageNum, pageSize);
    }

    protected LambdaQueryWrapper<T> getLambdaQueryWrapper() {
        return Wrappers.lambdaQuery();
    }
}
