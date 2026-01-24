package com.lambda.fusion.authority.user.helper;

import com.google.common.collect.Maps;
import com.lambda.fusion.authority.user.model.UserFieldsEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserInfoHelper {

    public static List<UserFieldsEntity> buildUserFieldsFromMap(Map<String, Object> personal, String username) {
        return getUserFieldsEntities(personal, username);
    }

    public static List<UserFieldsEntity> getUserFieldsEntities(Map<String, Object> personal, String username) {
        List<UserFieldsEntity> userFields = new ArrayList<>(personal.size());
        personal.forEach((k, v) -> {
            UserFieldsEntity info = new UserFieldsEntity();
            info.setUsername(username);
            info.setFieldName(k);
            info.setFieldValue((String) v);
            userFields.add(info);
        });
        return userFields;
    }

    /**
     * 构造 用户扩展信息对象
     *
     * @param fields 所有的数据
     * @return Map<String, Map < String, String>>
     */
    public static Map<String, Map<String, Object>> buildUserFieldsMap(List<UserFieldsEntity> fields) {
        Map<String, Map<String, Object>> maps = Maps.newHashMap();
        fields.forEach(userFieldsEntity -> {
            String username = userFieldsEntity.getUsername();
            Map<String, Object> map;
            if (!maps.containsKey(username)) {
                map = Maps.newHashMap();
            } else {
                map = maps.get(username);
            }
            map.put(userFieldsEntity.getFieldName(), userFieldsEntity.getFieldValue());
            maps.put(username, map);
        });
        return maps;
    }
}
