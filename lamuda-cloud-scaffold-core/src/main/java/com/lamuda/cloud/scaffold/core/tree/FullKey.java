package com.lamuda.cloud.scaffold.core.tree;


import com.lamuda.cloud.scaffold.core.Constants;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Objects;

/**
 * FullKey
 *
 * @author jin
 */
@Data
@AllArgsConstructor
public class FullKey {
    private Object key;
    private FullKey parentKey;

    public String getKey() {
        if (Objects.isNull(parentKey) || Objects.isNull(parentKey.getKey())) {
            if (Objects.isNull(key)) {
                return null;
            } else {
                return key.toString();
            }
        }
        return parentKey.getKey() + Constants.JOINER + key;
    }
}
