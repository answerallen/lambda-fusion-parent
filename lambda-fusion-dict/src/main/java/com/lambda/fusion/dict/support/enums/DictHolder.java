package com.lambda.fusion.dict.support.enums;

import com.lambda.fusion.dict.support.model.DynamicDictSource;
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
    private List<DynamicDictSource> list;

    public DictHolder(String dictName, String dictDesc) {
        this.dictName = dictName;
        this.dictDesc = dictDesc;
        this.list = new ArrayList<>();
    }

    public DictHolder addOption(String key, Object val) {
        return addOption(new DynamicDictSource(key, val));
    }

    public DictHolder addOption(DynamicDictSource dictOption) {
        list.add(dictOption);
        return this;
    }
}
