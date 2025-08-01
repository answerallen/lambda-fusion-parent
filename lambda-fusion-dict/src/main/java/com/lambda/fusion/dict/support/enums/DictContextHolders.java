package com.lambda.fusion.dict.support.enums;

import com.lambda.fusion.dict.entity.DictInfo;
import com.lambda.fusion.dict.entity.DictType;
import com.lambda.fusion.dict.support.model.DictValueType;
import com.lambda.fusion.dict.support.model.DynamicDict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jin
 */
public class DictContextHolders {
    protected static final Map<String, DictHolder> MAPPER_HOLDERS = new ConcurrentHashMap<>();

    protected static final Map<String, DictType> DICT_TYPES = new ConcurrentHashMap<>();

    private DictContextHolders() {}

    public static Map<String, DictHolder> getMapperHolders() {
        return MAPPER_HOLDERS;
    }

    public static Map<String, DictType> getDictTypes() {
        return DICT_TYPES;
    }

    public static void addDictHolder(DictHolder dictHolder) {
        MAPPER_HOLDERS.put(dictHolder.getDictName(), dictHolder);
        DICT_TYPES.put(dictHolder.getDictName(), convertToDictType(dictHolder));
    }

    public static DictHolder getDictHolder(String dictName) {
        return MAPPER_HOLDERS.get(dictName);
    }

    public static DictType getDictType(String dictName) {
        return DICT_TYPES.get(dictName);
    }

    public static List<DictType> getDictTypeList() {
        return new ArrayList<>(DICT_TYPES.values());
    }

    private static DictType convertToDictType(DictHolder dictHolder) {
        final DictType dictType = new DictType();
        dictType.setId(dictHolder.getDictName());
        dictType.setDictType(dictHolder.getDictName());
        dictType.setDataType(DictValueType.ENUM_DICT.getValueType());
        dictType.setDictName(dictHolder.getDictDesc());
        dictType.setLevel(1);

        final List<DynamicDict> dictHolderList = dictHolder.getList();
        List<DictInfo> dictInfos = new ArrayList<>(dictHolderList.size());
        for (DynamicDict dynamicDict : dictHolderList) {
            final DictInfo dictInfo = new DictInfo();
            dictInfo.setId(dynamicDict.getKey());
            dictInfo.setDictType(dictType.getDictType());
            dictInfo.setDictName(dictType.getDictName());
            dictInfo.setFieldType(dynamicDict.getVal().toString());
            dictInfo.setFieldName(dynamicDict.getKey());
            dictInfos.add(dictInfo);
        }
        dictType.setData(dictInfos);
        return dictType;
    }
}
