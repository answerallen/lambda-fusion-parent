package com.lambda.fusion.core.service;

import cn.hutool.core.util.TypeUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.convert.BaseConverter;
import com.lambda.cloud.core.convert.ConverterResolver;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/**
 * AbstractCrudService 是一个抽象类，继承了 ServiceImpl 并提供了实体与值对象（VO）之间转换的通用功能。
 * 它使用泛型参数 E 表示实体，V 表示值对象，M 表示必须继承 BaseMapper 的映射器。
 *
 * <p>该类包含用于将单个实体或实体列表转换为相应值对象的方法，以及基于查询条件获取分页或非分页值对象列表的方法。
 * 实体与值对象之间的转换由转换器处理，转换器通过 ConverterResolver 进行解析。
 */
@SuppressWarnings("unused")
public abstract class AbstractCrudService<E, V, M extends BaseMapper<E>> extends ServiceImpl<M, E> {
    private Class<V> voClass;

    private BaseConverter<E, V> converter() {
        if (voClass == null) {
            Type type = TypeUtil.getTypeArgument(getClass(), 1);
            if (type instanceof ParameterizedType) {
                //noinspection unchecked
                this.voClass = (Class<V>) ((ParameterizedType) type).getActualTypeArguments()[1];
            }else if (type instanceof Class<?>) {
                //noinspection unchecked
                this.voClass = (Class<V>) type;
            } else {
                throw new IllegalStateException("子类必须继承 AbstractCrudService<E, V, M extends BaseMapper<E>> 并固化泛型");
            }
        }
        return ConverterResolver.getConverter(voClass);
    }

    /**
     * 将实体列表转换为值对象（VO）列表。
     *
     * @param entity 要转换的实体列表
     * @return 与输入实体对应的值对象列表
     */
    public List<V> toVO(List<E> entity) {
        return converter().convertToList(entity);
    }

    /**
     * 将实体转换为值对象（VO）。
     *
     * @param entity 要转换的实体
     * @return 与输入实体对应的值对象
     */
    public V toVO(E entity) {
        return converter().convertTo(entity);
    }

    /**
     * 根据提供的分页对象和查询包装器获取值对象（VO）分页。
     *
     * @param page         包含分页信息的分页对象
     * @param queryWrapper 用于构造查询的包装器
     * @return 与输入实体对应的值对象分页
     */
    public IPage<V> pageForVO(IPage<E> page, Wrapper<E> queryWrapper) {
        IPage<E> entityPage = super.page(page, queryWrapper);
        return convertPageToVO(entityPage);
    }

    /**
     * 根据提供的分页对象获取值对象（VO）分页。
     *
     * @param page 包含分页信息的分页对象
     * @return 与输入实体对应的值对象分页
     */
    public IPage<V> pageForVO(IPage<E> page) {
        IPage<E> entityPage = super.page(page);
        return convertPageToVO(entityPage);
    }

    /**
     * 根据提供的查询包装器获取值对象（VO）列表。
     *
     * @param queryWrapper 用于构造查询的包装器
     * @return 与输入实体对应的值对象列表
     */
    public List<V> listForVO(Wrapper<E> queryWrapper) {
        List<E> entityList = super.list(queryWrapper);
        return convertListToVO(entityList);
    }

    /**
     * 获取所有实体的值对象（VO）列表。
     *
     * @return 对应所有实体的值对象列表
     */
    public List<V> listForVO() {
        List<E> entityList = super.list();
        return convertListToVO(entityList);
    }

    /**
     * 获取与给定查询包装器匹配的实体的值对象（VO）。
     *
     * @param queryWrapper 用于构造查询的包装器
     * @return 与找到的实体对应的值对象，如果未找到匹配的实体则返回 null
     */
    public V getForVO(Wrapper<E> queryWrapper) {
        E entity = super.getOne(queryWrapper);
        return toVO(entity);
    }

    /**
     * 根据指定 ID 获取实体的值对象（VO）。
     *
     * @param id 要获取的实体的 ID
     * @return 与找到的实体对应的值对象，如果未找到匹配的实体则返回 null
     */
    public V getByIdForVO(Serializable id) {
        E entity = super.getById(id);
        return toVO(entity);
    }

    /**
     * 将实体分页转换为值对象（VO）分页。
     *
     * @param entityPage 要转换的实体分页
     * @return 与输入实体对应的值对象分页
     */
    private IPage<V> convertPageToVO(IPage<E> entityPage) {
        return entityPage.convert(this::toVO);
    }

    /**
     * 使用内部转换机制将实体列表转换为值对象（VO）列表。
     *
     * @param entityList 要转换的实体列表
     * @return 与输入实体对应的值对象列表
     */
    private List<V> convertListToVO(List<E> entityList) {
        return toVO(entityList);
    }
}
