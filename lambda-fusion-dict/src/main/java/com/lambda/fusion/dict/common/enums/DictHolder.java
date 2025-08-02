package com.lambda.fusion.dict.common.enums;

import com.lambda.fusion.dict.common.model.DynamicDict;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 *
 * @author Jin
 */
@Data
@SuppressFBWarnings("EI_EXPOSE_REP")
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
