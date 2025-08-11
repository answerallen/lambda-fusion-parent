package com.lambda.fusion.dict.service.impl;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.lambda.cloud.core.exception.NotSupportedException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

@Slf4j
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class NacosConfigService {

    private static final String YML = "yml";
    private static final String YAML = "yaml";
    private static final String[] TYPES = {YML, YAML, "properties"};
    private final ConfigService configService;
    private final String groupid;

    public NacosConfigService(ConfigService configService, String groupid) {
        this.groupid = groupid;
        this.configService = configService;
    }

    /**
     * 发布一个配置
     *
     * <pre>
     *     String content = YamlUtils.dump(config);
     *     nacosConfigService.publishConfig("demo.yml", content);
     * </pre>
     *
     * @param dataid
     * @param content
     * @throws NacosException
     */
    public void publishConfig(String dataid, String content) {
        publishConfig(dataid, groupid, content);
    }

    /**
     * 发布一个配置
     *
     * <pre>
     *     String content = YamlUtils.dump(config);
     *     nacosConfigService.publishConfig("demo.yml", "default", content);
     * </pre>
     *
     * @param dataid
     * @param groupid
     * @param content
     * @return void
     */
    public void publishConfig(String dataid, String groupid, String content) {
        String type = getType(dataid);
        try {
            configService.publishConfig(dataid, groupid, content, type);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 获取配置类型
     *
     * @param dataid
     * @return java.lang.String
     */
    private String getType(String dataid) {
        String type = FilenameUtils.getExtension(dataid);
        if (StringUtils.isBlank(type)) {
            type = "properties";
        }
        if (!ArrayUtils.contains(TYPES, type)) {
            throw new NotSupportedException("暂不支持该类型的配置文件!, type: " + type);
        }
        if (YML.equals(type)) {
            type = YAML;
        }
        return type;
    }
}
