package com.lambda.fusion.dict.commons.registry;

import com.lambda.fusion.dict.commons.DictValueType;
import com.lambda.fusion.dict.model.DictInfo;
import com.lambda.fusion.dict.model.DictTypeTree;
import com.lambda.fusion.dict.model.DynamicDictSource;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jin
 */
@SuppressFBWarnings("MS_EXPOSE_REP")
public class DictRegistry {
    protected static final Map<String, DictHolder> MAPPER_HOLDERS = new ConcurrentHashMap<>();

    protected static final Map<String, DictTypeTree> DICT_TYPES = new ConcurrentHashMap<>();

    private DictRegistry() {}

    @SuppressWarnings("MS_EXPOSE_REP")
    public static Map<String, DictHolder> getMapperHolders() {
        return MAPPER_HOLDERS;
    }

    @SuppressWarnings("MS_EXPOSE_REP")
    public static Map<String, DictTypeTree> getDictTypes() {
        return DICT_TYPES;
    }

    public static void addDictHolder(DictHolder dictHolder) {
        MAPPER_HOLDERS.put(dictHolder.getDictName(), dictHolder);
        DICT_TYPES.put(dictHolder.getDictName(), convertToDictType(dictHolder));
    }

    public static DictHolder getDictHolder(String dictName) {
        return MAPPER_HOLDERS.get(dictName);
    }

    public static DictTypeTree getDictType(String dictName) {
        return DICT_TYPES.get(dictName);
    }

    public static List<DictTypeTree> getDictTypeList() {
        return new ArrayList<>(DICT_TYPES.values());
    }

    private static DictTypeTree convertToDictType(DictHolder dictHolder) {
        final DictTypeTree dictTypeTree = new DictTypeTree();
        dictTypeTree.setId(dictHolder.getDictName());
        dictTypeTree.setDictType(dictHolder.getDictName());
        dictTypeTree.setDataType(DictValueType.ENUM_DICT.getCode());
        dictTypeTree.setDictName(dictHolder.getDictDesc());
        dictTypeTree.setDictUsage(dictHolder.getDictUsage());
        dictTypeTree.setLevel(1);

        final List<DynamicDictSource> dictHolderList = dictHolder.getList();
        List<DictInfo> dictionaryEntries = new ArrayList<>(dictHolderList.size());
        for (DynamicDictSource dynamicDictSource : dictHolderList) {
            final DictInfo dictInfo = new DictInfo();
            dictInfo.setId(dynamicDictSource.getKey());
            dictInfo.setDictType(dictTypeTree.getDictType());
            dictInfo.setDictName(dictTypeTree.getDictName());
            dictInfo.setFieldType(dynamicDictSource.getVal().toString());
            dictInfo.setFieldName(dynamicDictSource.getKey());
            dictionaryEntries.add(dictInfo);
        }
        dictTypeTree.setData(dictionaryEntries);
        return dictTypeTree;
    }
}
