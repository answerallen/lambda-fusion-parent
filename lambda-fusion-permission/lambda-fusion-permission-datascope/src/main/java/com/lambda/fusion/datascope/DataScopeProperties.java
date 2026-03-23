package com.lambda.fusion.datascope;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.ArrayUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = DataScopeConstants.PREFIX)
public class DataScopeProperties {

    public static final Integer[] EMPTY_TYPES = new Integer[0];

    private Smart smart = new Smart();

    public Integer[] getSmartTypesByBusiness(String businessKey) {
        if (!smart.isEnabled()) {
            return EMPTY_TYPES;
        }
        Integer[] types = smart.getTypes().get(businessKey);
        if (ArrayUtils.isEmpty(types)) {
            return EMPTY_TYPES;
        }
        return types;
    }

    @Setter
    @Getter
    public static class Smart {
        private boolean enabled = false;
        private Map<String, Integer[]> types = new HashMap<>();

    }
}
