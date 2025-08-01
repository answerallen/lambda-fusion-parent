package com.lambda.fusion.dict.support.resolve;

import cn.hutool.core.text.CharSequenceUtil;
import com.lambda.fusion.dict.entity.DictType;
import com.lambda.fusion.dict.support.model.DynamicDict;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

/**
 * UrlDictResolveExt
 *
 * @author Jin
 */
@Slf4j
public class UrlDictResolveExt extends UrlDictResolve {

    @Override
    public boolean isSupport(Integer valueType) {
        return false;
    }

    public List<DynamicDict> doResolve(DictType dictType, String accessToken) {
        String url = dictType.getDataTypeValue();
        if (CharSequenceUtil.isEmpty(accessToken) || url.startsWith(HTTP_PROTOCOL) || url.startsWith(HTTPS_PROTOCOL)) {
            return super.doResolve(dictType);
        } else {
            String localUrl = getLocalDictUrl(url);
            return getApi(localUrl, HttpMethod.GET, accessToken);
        }
    }
}
