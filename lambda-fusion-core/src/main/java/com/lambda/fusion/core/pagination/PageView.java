package com.lambda.fusion.core.pagination;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 分页视图对象，用于返回。
 *
 * <p>
 * 与分页查询参数对象分离，专注表达分页结果数据，并支持从 MyBatis Plus {@link IPage} 转换。
 *
 * @param <T> 记录类型
 */
@SuppressFBWarnings("EI_EXPOSE_REP")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "分页结果视图")
public class PageView<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "当前页码，从1开始", example = "1")
    private long pageNum;

    @Schema(description = "每页条数", example = "10")
    private long pageSize;

    @Schema(description = "总记录数", example = "125")
    private long total;

    @Schema(description = "总页数", example = "13")
    private long totalPages;

    @Schema(description = "当前页数据")
    @Builder.Default
    private List<T> records = Collections.emptyList();

    @Schema(description = "是否存在上一页", example = "false")
    private boolean hasPrevious;

    @Schema(description = "是否存在下一页", example = "true")
    private boolean hasNext;

    /**
     * 从 MyBatis Plus 分页对象转换为分页视图。
     *
     * @param page MyBatis Plus 分页对象
     * @param <T> 记录类型
     * @return 分页视图
     */
    public static <T> PageView<T> of(IPage<T> page) {
        Objects.requireNonNull(page, "page must not be null");
        long current = page.getCurrent();
        long size = page.getSize();
        long pages = page.getPages();
        return PageView.<T>builder()
                .pageNum(current)
                .pageSize(size)
                .total(page.getTotal())
                .totalPages(pages)
                .records(safeRecords(page.getRecords()))
                .hasPrevious(current > 1)
                .hasNext(current < pages)
                .build();
    }

    /**
     * 从 MyBatis Plus 分页对象转换为分页视图，并映射记录类型。
     *
     * @param page MyBatis Plus 分页对象
     * @param mapper 记录映射函数
     * @param <S> 源记录类型
     * @param <T> 目标记录类型
     * @return 分页视图
     */
    public static <S, T> PageView<T> of(IPage<S> page, Function<? super S, ? extends T> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return PageView.of(page.convert(mapper));
    }

    /**
     * 是否为空页。
     *
     * @return 当前页是否没有数据
     */
    public boolean isEmpty() {
        return safeRecords(this.records).isEmpty();
    }

    private static <T> List<T> safeRecords(List<T> records) {
        return records == null ? Collections.emptyList() : List.copyOf(records);
    }
}
