package com.lamuda.cloud.scaffold.dict.support.resolve;

import cn.hutool.core.text.CharSequenceUtil;
import com.lamuda.cloud.scaffold.dict.support.model.DynamicDict;
import com.lamuda.cloud.fx.dict.entity.DictType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

import java.util.List;

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
