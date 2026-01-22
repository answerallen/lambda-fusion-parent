package com.lambda.fusion.dict.support.enums;

import com.lambda.fusion.dict.model.DictInfo;
import com.lambda.fusion.dict.model.DictTypeTree;
import com.lambda.fusion.dict.support.model.DictValueType;
import com.lambda.fusion.dict.support.model.DynamicDictionarySource;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jin
 */
@SuppressFBWarnings("MS_EXPOSE_REP")
public class DictionaryRegistry {
    protected static final Map<String, DictionaryHolder> MAPPER_HOLDERS = new ConcurrentHashMap<>();

    protected static final Map<String, DictTypeTree> DICT_TYPES = new ConcurrentHashMap<>();

    private DictionaryRegistry() {}

    @SuppressWarnings("MS_EXPOSE_REP")
    public static Map<String, DictionaryHolder> getMapperHolders() {
        return MAPPER_HOLDERS;
    }

    @SuppressWarnings("MS_EXPOSE_REP")
    public static Map<String, DictTypeTree> getDictTypes() {
        return DICT_TYPES;
    }

    public static void addDictHolder(DictionaryHolder dictionaryHolder) {
        MAPPER_HOLDERS.put(dictionaryHolder.getDictName(), dictionaryHolder);
        DICT_TYPES.put(dictionaryHolder.getDictName(), convertToDictType(dictionaryHolder));
    }

    public static DictionaryHolder getDictHolder(String dictName) {
        return MAPPER_HOLDERS.get(dictName);
    }

    public static DictTypeTree getDictType(String dictName) {
        return DICT_TYPES.get(dictName);
    }

    public static List<DictTypeTree> getDictTypeList() {
        return new ArrayList<>(DICT_TYPES.values());
    }

    private static DictTypeTree convertToDictType(DictionaryHolder dictionaryHolder) {
        final DictTypeTree dictTypeTree = new DictTypeTree();
        dictTypeTree.setId(dictionaryHolder.getDictName());
        dictTypeTree.setDictType(dictionaryHolder.getDictName());
        dictTypeTree.setDataType(DictValueType.ENUM_DICT.getValueType());
        dictTypeTree.setDictName(dictionaryHolder.getDictDesc());
        dictTypeTree.setLevel(1);

        final List<DynamicDictionarySource> dictHolderList = dictionaryHolder.getList();
        List<DictInfo> dictionaryEntries = new ArrayList<>(dictHolderList.size());
        for (DynamicDictionarySource dynamicDictionarySource : dictHolderList) {
            final DictInfo dictionaryEntry = new DictInfo();
            dictionaryEntry.setId(dynamicDictionarySource.getKey());
            dictionaryEntry.setDictType(dictTypeTree.getDictType());
            dictionaryEntry.setDictName(dictTypeTree.getDictName());
            dictionaryEntry.setFieldType(dynamicDictionarySource.getVal().toString());
            dictionaryEntry.setFieldName(dynamicDictionarySource.getKey());
            dictionaryEntries.add(dictionaryEntry);
        }
        dictTypeTree.setData(dictionaryEntries);
        return dictTypeTree;
    }
}
