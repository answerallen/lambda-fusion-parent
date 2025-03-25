package com.lamuda.cloud.scaffold.dict.support.resolve;

import cn.hutool.core.text.CharSequenceUtil;
import com.lamuda.cloud.fx.core.Constants;
import com.lamuda.cloud.scaffold.dict.config.DictionaryProperties;
import com.lamuda.cloud.scaffold.dict.support.model.DictValueType;
import com.lamuda.cloud.scaffold.dict.support.model.DynamicDict;
import com.lamuda.cloud.fx.dict.entity.DictType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

import static com.lamuda.cloud.fx.core.Constants.AUTHORIZATION;
import static com.lamuda.cloud.fx.core.Constants.BEARER;
import static com.lamuda.cloud.mvc.WebHttpUtils.X_AUTHORIZED_BEARER;

/**
 * @author jin
 */
@Slf4j
@Service
public class UrlDictResolve implements IDynamicDictResolve {

    public static final String HTTP_PROTOCOL = "http";
    public static final String HTTPS_PROTOCOL = "https";

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
        return DictValueType.URL_DICT.getValueType().equals(valueType);
    }

    @Override
    public List<DynamicDict> doResolve(DictType dictType) {
        String url = dictType.getDataTypeValue();
        try {
            if (url.startsWith(HTTP_PROTOCOL) || url.startsWith(HTTPS_PROTOCOL)) {
                return getApi(url, HttpMethod.GET);
            } else {
                ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                HttpServletRequest request = Objects.requireNonNull(servletRequestAttributes).getRequest();
                String accessToken = getAccessToken(request);
                url = getLocalDictUrl(url);
                return getApi(url, HttpMethod.GET, accessToken);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("字典http请求错误", e);
        }
    }

    protected String getLocalDictUrl(String url) {
        String httpRemoteHostPrefix = dictionaryProperties.getHttpRemoteHostPrefix();
        if (CharSequenceUtil.isNotEmpty(httpRemoteHostPrefix)) {
            url = httpRemoteHostPrefix + url;
        } else {
            String protocol = (httpFlag ? HTTP_PROTOCOL : HTTPS_PROTOCOL) + "://";
            url = protocol + host + Constants.COLON + port + contextPath + url;
        }
        return url;
    }


    public List<DynamicDict> getApi(final String path, final HttpMethod method) {
        final ResponseEntity<List<DynamicDict>> response = restTemplate.exchange(
                path,
                method,
                null,
                new ListParameterizedTypeReference());
        return response.getBody();
    }

    public List<DynamicDict> getApi(final String path, final HttpMethod method, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        final ResponseEntity<List<DynamicDict>> response = restTemplate.exchange(
                path,
                method,
                new HttpEntity<>(headers),
                new ListParameterizedTypeReference());
        return response.getBody();
    }

    private static class ListParameterizedTypeReference extends ParameterizedTypeReference<List<DynamicDict>> {
    }

    public static String getAccessToken(HttpServletRequest request) {
        String payload = request.getHeader(AUTHORIZATION);
        if (StringUtils.isNotBlank(payload) && payload.startsWith(BEARER)) {
            return payload.replace(BEARER, StringUtils.EMPTY);
        }
        Cookie cookie = WebUtils.getCookie(request, X_AUTHORIZED_BEARER);
        if (cookie != null && StringUtils.isNotBlank(cookie.getValue())) {
            return cookie.getValue();
        }
        return null;
    }
}
