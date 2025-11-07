package com.lambda.fusion.core.tree.model;

import com.lambda.fusion.core.Constants;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * TreeNodeKey
 *
 * @author jin
 */
@Data
@AllArgsConstructor
@SuppressFBWarnings({"EI_EXPOSE_REP"})
public class TreeNodeKey {
    private Object key;
    private TreeNodeKey parentKey;

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
