package com.lambda.fusion.ai.commons.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.datasource.TenantDataSourceHelper;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.datasource.tenant.TenantDataSourceManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantDataSourceHelperTest {

    @Mock
    private TenantDataSourceManager tenantDataSourceManager;

    @Test
    @DisplayName("空租户使用默认数据源")
    void testResolveDefaultDataSource() {
        AiProperties properties = new AiProperties();
        TenantDataSourceHelper helper = new TenantDataSourceHelper(tenantDataSourceManager, properties);

        assertThat(helper.resolveTargetDataSourceName(null))
                .isEqualTo(properties.getDataSource().getName());
        assertThat(helper.resolveTargetDataSourceName("default"))
                .isEqualTo(properties.getDataSource().getName());
    }

    @Test
    @DisplayName("存在租户数据源时返回租户数据源名称")
    void testResolveTenantDataSource() {
        AiProperties properties = new AiProperties();
        TenantDataSourceHelper helper = new TenantDataSourceHelper(tenantDataSourceManager, properties);
        when(tenantDataSourceManager.tenantDataSourceExists(
                        "tenant-a", properties.getDataSource().getTenantPrefix()))
                .thenReturn(true);
        when(tenantDataSourceManager.getTenantDataSourceName(
                        "tenant-a", properties.getDataSource().getTenantPrefix()))
                .thenReturn("ai-tenant-tenant-a");

        assertThat(helper.resolveTargetDataSourceName("tenant-a")).isEqualTo("ai-tenant-tenant-a");
    }

    @Test
    @DisplayName("缺失租户数据源时抛出异常")
    void testResolveMissingTenantDataSource() {
        AiProperties properties = new AiProperties();
        TenantDataSourceHelper helper = new TenantDataSourceHelper(tenantDataSourceManager, properties);
        when(tenantDataSourceManager.tenantDataSourceExists(
                        "tenant-missing", properties.getDataSource().getTenantPrefix()))
                .thenReturn(false);

        assertThatThrownBy(() -> helper.resolveTargetDataSourceName("tenant-missing"))
                .isInstanceOf(AiBusinessException.class);
    }
}
