package com.lambda.fusion.dict.support;

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
    private Integer dictUsage;
    private List<DynamicDictSource> list;

    public DictHolder(String dictName, String dictDesc,int dictUsage) {
        this.dictName = dictName;
        this.dictDesc = dictDesc;
        this.list = new ArrayList<>();
        this.dictUsage = dictUsage;
    }

    public void addOption(String key, Object val) {
        addOption(new DynamicDictSource(key, val));
    }

    public void addOption(DynamicDictSource dictOption) {
        list.add(dictOption);
    }
}
