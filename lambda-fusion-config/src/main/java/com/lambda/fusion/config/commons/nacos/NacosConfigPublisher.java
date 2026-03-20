package com.lambda.fusion.config.commons.nacos;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.lambda.cloud.nacos.Nacos;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class NacosConfigPublisher {

    private final ConfigService configService;
    private final String groupid;

    public NacosConfigPublisher(ConfigService configService, String groupid) {
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
            configService.publishConfig(dataid, Nacos.resolveGroup(groupid), content, type);
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
        return Nacos.resolveConfigType(dataid);
    }
}
