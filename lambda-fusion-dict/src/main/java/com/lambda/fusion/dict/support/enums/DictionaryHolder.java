package com.lambda.fusion.dict.support.enums;

import com.lambda.fusion.dict.support.model.DynamicDictionarySource;
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
public class DictionaryHolder {
    private String dictName;
    private String dictDesc;
    private List<DynamicDictionarySource> list;

    public DictionaryHolder(String dictName, String dictDesc) {
        this.dictName = dictName;
        this.dictDesc = dictDesc;
        this.list = new ArrayList<>();
    }

    public DictionaryHolder addOption(String key, Object val) {
        return addOption(new DynamicDictionarySource(key, val));
    }

    public DictionaryHolder addOption(DynamicDictionarySource dictOption) {
        list.add(dictOption);
        return this;
    }
}
