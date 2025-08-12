package com.lambda.fusion.core.base.page;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lambda.fusion.core.Constants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 分页查询基类
 * 
 * <p>提供统一的分页查询功能，支持分页参数校验、排序、默认值处理等特性。
 * 遵循阿里巴巴Java开发规范和Spring Boot最佳实践。
 * 
 * <h3>功能特性：</h3>
 * <ul>
 * <li><strong>分页参数校验：</strong>支持页码和页大小的范围校验</li>
 * <li><strong>默认值处理：</strong>提供合理的默认分页参数</li>
 * <li><strong>排序支持：</strong>支持多字段排序，包括升序和降序</li>
 * <li><strong>安全限制：</strong>限制最大页大小，防止大数据量查询</li>
 * <li><strong>MyBatis Plus集成：</strong>无缝集成MyBatis Plus分页插件</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * public class UserPageQueryDTO extends PageQuery<UserEntity> {
 *     private String name;
 *     private String email;
 *     
 *     // getter and setter...
 * }
 * 
 * // 在Controller中使用
 * @PostMapping("/page")
 * public Page<UserEntity> page(@Valid @RequestBody UserPageQueryDTO queryDTO) {
 *     Page<UserEntity> page = queryDTO.getPage();
 *     return userService.page(page, queryDTO);
 * }
 * }</pre>
 * 
 * @param <T> 实体类型
 */
@Getter
@Setter
@Schema(description = "分页查询基类")
public abstract class PageQuery<T> {
    
    /**
     * 默认页码
     */
    public static final int DEFAULT_PAGE_NUM = 1;
    
    /**
     * 默认页大小
     */
    public static final int DEFAULT_PAGE_SIZE = 20;
    
    /**
     * 最大页大小限制
     */
    public static final int MAX_PAGE_SIZE = 1000;
    
    /**
     * 最小页大小
     */
    public static final int MIN_PAGE_SIZE = 1;
    
    /**
     * 排序方向：升序
     */
    public static final String ORDER_ASC = "ASC";
    
    /**
     * 排序方向：降序
     */
    public static final String ORDER_DESC = "DESC";
    
    /**
     * 字段名安全校验正则表达式
     * 只允许字母、数字、下划线，防止SQL注入
     */
    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");
    
    /**
     * 当前页码
     * <p>页码从1开始，默认为1</p>
     */
    @Schema(description = "当前页码，从1开始", example = "1", defaultValue = "1")
    @NotNull(message = Constants.MSG_PAGE_NUM_NOT_NULL)
    @Min(value = 1, message = "页码必须大于等于1")
    private Integer pageNum = DEFAULT_PAGE_NUM;
    
    /**
     * 每页条数
     * <p>默认20条，最大1000条，防止大数据量查询影响性能</p>
     */
    @Schema(description = "每页条数", example = "20", defaultValue = "20")
    @NotNull(message = Constants.MSG_PAGE_SIZE_NOT_NULL)
    @Min(value = MIN_PAGE_SIZE, message = "每页条数必须大于等于1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数不能超过1000")
    private Integer pageSize = DEFAULT_PAGE_SIZE;
    
    /**
     * 排序字段
     * <p>支持多字段排序，字段名使用数据库列名或实体属性名</p>
     */
    @Schema(description = "排序字段，支持多字段排序", example = "createTime,updateTime")
    private String orderBy;
    
    /**
     * 排序方向
     * <p>支持ASC（升序）和DESC（降序），默认为ASC</p>
     * <p>当有多个排序字段时，可以用逗号分隔指定每个字段的排序方向</p>
     */
    @Schema(description = "排序方向，ASC升序/DESC降序", example = "ASC", allowableValues = {"ASC", "DESC"})
    private String orderDirection = ORDER_ASC;
    
    /**
     * 是否查询总数
     * <p>默认为true，设置为false可以提高查询性能，适用于不需要总数的场景</p>
     */
    @Schema(description = "是否查询总数", example = "true", defaultValue = "true")
    private Boolean searchCount = true;
    
    /**
     * 获取MyBatis Plus分页对象
     * 
     * <p>根据当前分页参数和排序条件创建Page对象，自动处理排序逻辑。
     * 
     * @return MyBatis Plus分页对象
     */
    public Page<T> getPage() {
        // 参数校验和默认值处理
        Integer currentPageNum = Optional.ofNullable(this.pageNum).orElse(DEFAULT_PAGE_NUM);
        Integer currentPageSize = Optional.ofNullable(this.pageSize).orElse(DEFAULT_PAGE_SIZE);
        
        // 创建分页对象
        Page<T> page = new PageDTO<>(currentPageNum, currentPageSize);
        
        // 设置是否查询总数
        page.setSearchCount(Optional.ofNullable(this.searchCount).orElse(true));
        
        // 处理排序
        if (StringUtils.isNotBlank(this.orderBy)) {
            List<OrderItem> orderItems = buildOrderItems();
            page.setOrders(orderItems);
        }
        
        return page;
    }
    
    /**
     * 获取LambdaQueryWrapper
     * 
     * <p>提供基础的查询包装器，子类可以重写此方法添加自定义查询条件。
     * 
     * @return LambdaQueryWrapper查询包装器
     */
    @JsonIgnore
    public LambdaQueryWrapper<T> getLambdaQueryWrapper() {
        return Wrappers.lambdaQuery();
    }
    
    /**
     * 构建排序项列表
     * 
     * <p>解析排序字段和排序方向，构建MyBatis Plus的OrderItem列表。
     * 支持多字段排序，格式如："field1,field2" 配合 "ASC,DESC"。
     * 
     * @return 排序项列表
     */
    private List<OrderItem> buildOrderItems() {
        List<OrderItem> orderItems = new ArrayList<>();
        
        if (StringUtils.isBlank(this.orderBy)) {
            return orderItems;
        }
        
        // 解析排序字段
        String[] fields = this.orderBy.split(",");
        String[] directions = Optional.ofNullable(this.orderDirection)
                .map(dir -> dir.split(","))
                .orElse(new String[]{ORDER_ASC});
        
        for (int i = 0; i < fields.length; i++) {
            String field = fields[i].trim();
            if (StringUtils.isNotBlank(field)) {
                // 安全校验排序字段
                if (isValidFieldName(field)) {
                    throw new IllegalArgumentException("Invalid sort field: " + field + ". Field name must contain only letters, numbers and underscores.");
                }
                
                // 获取对应的排序方向，如果方向数组长度不够，使用最后一个方向
                String direction = i < directions.length ? directions[i].trim() : directions[directions.length - 1].trim();
                
                // 验证排序方向
                boolean isAsc = !ORDER_DESC.equalsIgnoreCase(direction);
                
                // 转换为下划线命名（数据库列名格式）
                String columnName = camelToUnderscore(field);
                OrderItem orderItem = new OrderItem();
                orderItem.setColumn(columnName);
                orderItem.setAsc(isAsc);
                orderItems.add(orderItem);
            }
        }
        
        return orderItems;
    }
    
    /**
     * 驼峰命名转下划线命名
     * 
     * <p>将Java属性名（驼峰命名）转换为数据库列名（下划线命名）。
     * 例如：createTime -> create_time
     * 
     * @param camelCase 驼峰命名字符串
     * @return 下划线命名字符串
     */
    private String camelToUnderscore(String camelCase) {
        if (StringUtils.isBlank(camelCase)) {
            return camelCase;
        }
        
        // 安全校验：防止SQL注入
        if (!FIELD_NAME_PATTERN.matcher(camelCase).matches()) {
            throw new IllegalArgumentException("Invalid field name: " + camelCase + ". Field name must contain only letters, numbers and underscores.");
        }
        
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
    
    /**
     * 校验排序字段的安全性
     * 
     * @param field 字段名
     * @return 是否安全
     */
    private boolean isValidFieldName(String field) {
        return !StringUtils.isNotBlank(field) || !FIELD_NAME_PATTERN.matcher(field).matches();
    }
    
    /**
     * 设置排序条件
     * 
     * <p>便捷方法，用于设置单个字段的排序条件。
     * 
     * @param field 排序字段
     * @param isAsc 是否升序
     * @return 当前对象，支持链式调用
     * @throws IllegalArgumentException 当字段名不安全时抛出
     */
    @SuppressWarnings("unchecked")
    public <Q extends PageQuery<T>> Q orderBy(String field, boolean isAsc) {
        if (StringUtils.isNotBlank(field) && isValidFieldName(field)) {
            throw new IllegalArgumentException("Invalid sort field: " + field + ". Field name must contain only letters, numbers and underscores.");
        }
        this.orderBy = field;
        this.orderDirection = isAsc ? ORDER_ASC : ORDER_DESC;
        return (Q) this;
    }
    
    /**
     * 设置升序排序
     * 
     * @param field 排序字段
     * @return 当前对象，支持链式调用
     */
    public <Q extends PageQuery<T>> Q orderByAsc(String field) {
        return orderBy(field, true);
    }
    
    /**
     * 设置降序排序
     * 
     * @param field 排序字段
     * @return 当前对象，支持链式调用
     */
    public <Q extends PageQuery<T>> Q orderByDesc(String field) {
        return orderBy(field, false);
    }
    
    /**
     * 设置多字段排序
     * 
     * @param fields 排序字段数组
     * @param directions 排序方向数组
     * @return 当前对象，支持链式调用
     * @throws IllegalArgumentException 当字段名不安全时抛出
     */
    @SuppressWarnings("unchecked")
    public <Q extends PageQuery<T>> Q orderBy(String[] fields, String[] directions) {
        if (fields != null && fields.length > 0) {
            // 校验所有字段名的安全性
            for (String field : fields) {
                if (StringUtils.isNotBlank(field) && isValidFieldName(field.trim())) {
                    throw new IllegalArgumentException("Invalid sort field: " + field + ". Field name must contain only letters, numbers and underscores.");
                }
            }
            this.orderBy = String.join(",", fields);
            if (directions != null && directions.length > 0) {
                this.orderDirection = String.join(",", directions);
            }
        }
        return (Q) this;
    }
    
    /**
     * 禁用总数查询
     * 
     * <p>在不需要总数的场景下使用，可以提高查询性能。
     * 
     * @return 当前对象，支持链式调用
     */
    @SuppressWarnings("unchecked")
    public <Q extends PageQuery<T>> Q disableSearchCount() {
        this.searchCount = false;
        return (Q) this;
    }
    
    /**
     * 启用总数查询
     * 
     * @return 当前对象，支持链式调用
     */
    @SuppressWarnings("unchecked")
    public <Q extends PageQuery<T>> Q enableSearchCount() {
        this.searchCount = true;
        return (Q) this;
    }
    
    /**
     * 重置分页参数为默认值
     * 
     * @return 当前对象，支持链式调用
     */
    @SuppressWarnings("unchecked")
    public <Q extends PageQuery<T>> Q reset() {
        this.pageNum = DEFAULT_PAGE_NUM;
        this.pageSize = DEFAULT_PAGE_SIZE;
        this.orderBy = null;
        this.orderDirection = ORDER_ASC;
        this.searchCount = true;
        return (Q) this;
    }
    
    /**
     * 设置分页大小
     * 
     * @param size 分页大小
     * @return 当前对象，支持链式调用
     * @throws IllegalArgumentException 当分页大小超出范围时抛出
     */
    @SuppressWarnings("unchecked")
    public <Q extends PageQuery<T>> Q size(Integer size) {
        if (size != null && (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE)) {
            throw new IllegalArgumentException("Page size must be between " + MIN_PAGE_SIZE + " and " + MAX_PAGE_SIZE);
        }
        this.pageSize = size;
        return (Q) this;
    }
    
    /**
     * 设置页码
     * 
     * @param num 页码
     * @return 当前对象，支持链式调用
     * @throws IllegalArgumentException 当页码小于1时抛出
     */
    @SuppressWarnings("unchecked")
    public <Q extends PageQuery<T>> Q page(Integer num) {
        if (num != null && num < 1) {
            throw new IllegalArgumentException("Page number must be greater than 0");
        }
        this.pageNum = num;
        return (Q) this;
    }
    
    /**
     * 添加排序字段（追加模式）
     * 
     * @param field 排序字段
     * @param isAsc 是否升序
     * @return 当前对象，支持链式调用
     * @throws IllegalArgumentException 当字段名不安全时抛出
     */
    @SuppressWarnings("unchecked")
    public <Q extends PageQuery<T>> Q addOrderBy(String field, boolean isAsc) {
        if (StringUtils.isBlank(field)) {
            return (Q) this;
        }
        
        if (isValidFieldName(field)) {
            throw new IllegalArgumentException("Invalid sort field: " + field + ". Field name must contain only letters, numbers and underscores.");
        }
        
        String direction = isAsc ? ORDER_ASC : ORDER_DESC;
        
        if (StringUtils.isBlank(this.orderBy)) {
            this.orderBy = field;
            this.orderDirection = direction;
        } else {
            this.orderBy = this.orderBy + "," + field;
            this.orderDirection = this.orderDirection + "," + direction;
        }
        
        return (Q) this;
    }
    
    /**
     * 添加升序排序字段（追加模式）
     * 
     * @param field 排序字段
     * @return 当前对象，支持链式调用
     */
    public <Q extends PageQuery<T>> Q addOrderByAsc(String field) {
        return addOrderBy(field, true);
    }
    
    /**
     * 添加降序排序字段（追加模式）
     * 
     * @param field 排序字段
     * @return 当前对象，支持链式调用
     */
    public <Q extends PageQuery<T>> Q addOrderByDesc(String field) {
        return addOrderBy(field, false);
    }
    
    /**
     * 清除排序条件
     * 
     * @return 当前对象，支持链式调用
     */
    @SuppressWarnings("unchecked")
    public <Q extends PageQuery<T>> Q clearOrder() {
        this.orderBy = null;
        this.orderDirection = ORDER_ASC;
        return (Q) this;
    }
    
    /**
     * 检查是否有排序条件
     * 
     * @return 是否有排序条件
     */
    public boolean hasOrder() {
        return StringUtils.isNotBlank(this.orderBy);
    }
    
    /**
     * 获取排序字段数量
     * 
     * @return 排序字段数量
     */
    public int getOrderFieldCount() {
        if (StringUtils.isBlank(this.orderBy)) {
            return 0;
        }
        return this.orderBy.split(",").length;
    }
}
