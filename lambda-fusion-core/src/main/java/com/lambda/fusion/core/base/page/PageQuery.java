package com.lambda.fusion.core.base.page;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.lambda.fusion.core.Constants;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class PageQuery<T> {
    @NotNull(message = Constants.MSG_PAGE_NUM_NOT_NULL)
    private Integer pageNum = 1;

    @NotNull(message = Constants.MSG_PAGE_SIZE_NOT_NULL)
    private Integer pageSize = Integer.MAX_VALUE;

    public Page<T> getPage() {
        return new PageDTO<>(pageNum, pageSize);
    }

    protected LambdaQueryWrapper<T> getLambdaQueryWrapper() {
        return Wrappers.lambdaQuery();
    }
}
