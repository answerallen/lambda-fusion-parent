package com.lambda.fusion.dict.support.resolve;

import cn.hutool.core.text.CharSequenceUtil;
import com.lambda.fusion.dict.model.DictTypeTree;
import com.lambda.fusion.dict.DictConstants;
import com.lambda.fusion.dict.support.model.DynamicDictSource;
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

    public List<DynamicDictSource> doResolve(DictTypeTree dictTypeTree, String accessToken) {
        String url = dictTypeTree.getDataTypeValue();
        if (CharSequenceUtil.isEmpty(accessToken)
                || url.startsWith(DictConstants.HTTP_PROTOCOL)
                || url.startsWith(DictConstants.HTTPS_PROTOCOL)) {
            return super.doResolve(dictTypeTree);
        } else {
            String localUrl = getLocalDictUrl(url);
            return getApi(localUrl, HttpMethod.GET, accessToken);
        }
    }
}
