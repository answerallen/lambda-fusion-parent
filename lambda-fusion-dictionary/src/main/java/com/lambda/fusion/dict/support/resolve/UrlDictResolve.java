package com.lambda.fusion.dict.support.resolve;

import static com.lambda.cloud.mvc.WebHttpUtils.X_AUTHORIZED_BEARER;
import static com.lambda.fusion.dict.DictConstants.*;

import cn.hutool.core.text.CharSequenceUtil;
import com.lambda.fusion.autoconfig.DictionaryProperties;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.dict.model.DictTypeTree;
import com.lambda.fusion.dict.support.DictValueType;
import com.lambda.fusion.dict.support.model.DynamicDictSource;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.WebUtils;

/**
 * @author jin
 */
@Slf4j
@Service
@SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
public class UrlDictResolve implements DictSourceResolver {

    protected RestTemplate restTemplate = new RestTemplate();

    @Value("${server.port}")
    private String port;

    @Value("${server.servlet.context-path:/}")
    private String contextPath;

    @Value("${dict.service.http:false}")
    private boolean httpFlag;

    @Value("${dict.service.host:127.0.0.1}")
    private String host;

    @Resource
    private DictionaryProperties dictionaryProperties;

    @Override
    public boolean isSupport(Integer valueType) {
        return DictValueType.URL_DICT.getCode().equals(valueType);
    }

    @Override
    public List<DynamicDictSource> doResolve(DictTypeTree dictTypeTree) {
        String url = dictTypeTree.getDataTypeValue();
        try {
            if (url.startsWith(HTTP_PROTOCOL) || url.startsWith(HTTPS_PROTOCOL)) {
                return getApi(url, HttpMethod.GET);
            } else {
                HttpServletRequest request = Objects.requireNonNull(
                                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                        .getRequest();
                String accessToken = getAccessToken(request);
                url = getLocalDictUrl(url);
                return getApi(url, HttpMethod.GET, accessToken);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(ERROR_DICT_HTTP_REQUEST, e);
        }
    }

    protected String getLocalDictUrl(String url) {
        String httpRemoteHostPrefix = dictionaryProperties.getHttpRemoteHostPrefix();
        if (CharSequenceUtil.isNotEmpty(httpRemoteHostPrefix)) {
            url = httpRemoteHostPrefix + url;
        } else {
            String protocol = (httpFlag ? HTTP_PROTOCOL : HTTPS_PROTOCOL) + "://";
            url = protocol + host + FusionConstants.COLON + port + contextPath + url;
        }
        return url;
    }

    public List<DynamicDictSource> getApi(final String path, final HttpMethod method) {
        final ResponseEntity<List<DynamicDictSource>> response =
                restTemplate.exchange(path, method, null, new ListParameterizedTypeReference());
        return response.getBody();
    }

    public List<DynamicDictSource> getApi(final String path, final HttpMethod method, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        final ResponseEntity<List<DynamicDictSource>> response =
                restTemplate.exchange(path, method, new HttpEntity<>(headers), new ListParameterizedTypeReference());
        return response.getBody();
    }

    private static class ListParameterizedTypeReference extends ParameterizedTypeReference<List<DynamicDictSource>> {}

    public static String getAccessToken(HttpServletRequest request) {
        String payload = request.getHeader(FusionConstants.AUTHORIZATION);
        if (StringUtils.isNotBlank(payload) && payload.startsWith(FusionConstants.BEARER)) {
            return payload.replace(FusionConstants.BEARER, StringUtils.EMPTY);
        }
        Cookie cookie = WebUtils.getCookie(request, X_AUTHORIZED_BEARER);
        if (cookie != null && StringUtils.isNotBlank(cookie.getValue())) {
            return cookie.getValue();
        }
        return null;
    }
}
