package com.lamuda.cloud.scaffold.dict.support.enums;

import com.lamuda.cloud.scaffold.dict.support.model.DynamicDict;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Jin
 */
@Data
public class DictHolder {
    private String dictName;
    private String dictDesc;
    private List<DynamicDict> list;

    public DictHolder(String dictName, String dictDesc) {
        this.dictName = dictName;
        this.dictDesc = dictDesc;
        this.list = new ArrayList<>();
    }

    public DictHolder addOption(String key, Object val) {
        return addOption(new DynamicDict(key, val));
    }

    public DictHolder addOption(DynamicDict dictOption) {
        list.add(dictOption);
        return this;
    }




}
