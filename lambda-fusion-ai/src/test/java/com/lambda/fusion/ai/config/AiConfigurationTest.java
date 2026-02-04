package com.lambda.fusion.ai.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AiConfiguration的单元测试
 * 测试配置验证和修正功能
 */
@ExtendWith(MockitoExtension.class)
class AiConfigurationTest {

    private Document document;

    @BeforeEach
    void setUp() {
        document = new Document();
    }

    @Test
    void validateConfiguration_DefaultValues_ShouldPass() {
        // 给定 - 默认值: chunkSize=500, chunkOverlap=50
        
        // 当
        assertDoesNotThrow(() -> document.validateConfiguration());
        
        // 那么
        assertEquals(500, document.getDefaultChunkSize());
        assertEquals(50, document.getDefaultChunkOverlap());
    }

    @Test
    void validateConfiguration_OverlapEqualToChunkSize_ShouldCorrect() {
        // 给定
        document.setDefaultChunkSize(500);
        document.setDefaultChunkOverlap(500); // 与分块大小相同 - 无效
        
        // 当
        document.validateConfiguration();
        
        // 那么
        assertEquals(500, document.getDefaultChunkSize());
        assertEquals(50, document.getDefaultChunkOverlap()); // 应该修正为分块大小的10%
    }

    @Test
    void validateConfiguration_OverlapGreaterThanChunkSize_ShouldCorrect() {
        // 给定
        document.setDefaultChunkSize(400);
        document.setDefaultChunkOverlap(600); // 大于分块大小 - 无效
        
        // 当
        document.validateConfiguration();
        
        // 那么
        assertEquals(400, document.getDefaultChunkSize());
        assertEquals(40, document.getDefaultChunkOverlap()); // 应该修正为分块大小的10%
    }

    @Test
    void validateConfiguration_OverlapMoreThan50Percent_ShouldCorrect() {
        // 给定
        document.setDefaultChunkSize(100);
        document.setDefaultChunkOverlap(60); // 超过分块大小的50%
        
        // 当
        document.validateConfiguration();
        
        // 那么
        assertEquals(100, document.getDefaultChunkSize());
        assertEquals(10, document.getDefaultChunkOverlap()); // 应该修正为分块大小的10%
    }

    @Test
    void getValidatedChunkSize_NullInput_ShouldReturnDefault() {
        // 当
        int result = document.getValidatedChunkSize(null);
        
        // 那么
        assertEquals(500, result); // 默认分块大小
    }

    @Test
    void getValidatedChunkSize_ValidInput_ShouldReturnInput() {
        // 当
        int result = document.getValidatedChunkSize(800);
        
        // 那么
        assertEquals(800, result);
    }

    @Test
    void getValidatedChunkSize_TooSmall_ShouldReturnMinimum() {
        // 当
        int result = document.getValidatedChunkSize(50);
        
        // 那么
        assertEquals(100, result); // 允许的最小值
    }

    @Test
    void getValidatedChunkSize_TooLarge_ShouldReturnMaximum() {
        // 当
        int result = document.getValidatedChunkSize(3000);
        
        // 那么
        assertEquals(2000, result); // 允许的最大值
    }

    @Test
    void getValidatedChunkOverlap_NullInput_ShouldReturn10PercentOfChunkSize() {
        // 当
        int result = document.getValidatedChunkOverlap(null, 500);
        
        // 那么
        assertEquals(50, result); // 500的10%
    }

    @Test
    void getValidatedChunkOverlap_NullInputSmallChunkSize_ShouldReturnMinimum() {
        // 当
        int result = document.getValidatedChunkOverlap(null, 80);
        
        // 那么
        assertEquals(10, result); // 最小值为10，即使80的10%是8
    }

    @Test
    void getValidatedChunkOverlap_ValidInput_ShouldReturnInput() {
        // 当
        int result = document.getValidatedChunkOverlap(100, 500);
        
        // 那么
        assertEquals(100, result);
    }

    @Test
    void getValidatedChunkOverlap_TooSmall_ShouldReturnMinimum() {
        // 当
        int result = document.getValidatedChunkOverlap(5, 500);
        
        // 那么
        assertEquals(10, result); // 允许的最小值
    }

    @Test
    void getValidatedChunkOverlap_EqualToChunkSize_ShouldCorrect() {
        // 当
        int result = document.getValidatedChunkOverlap(500, 500);
        
        // 那么
        assertEquals(50, result); // 应该修正为分块大小的10%
    }

    @Test
    void getValidatedChunkOverlap_GreaterThanChunkSize_ShouldCorrect() {
        // 当
        int result = document.getValidatedChunkOverlap(600, 500);
        
        // 那么
        assertEquals(50, result); // 应该修正为分块大小的10%
    }

    @Test
    void getValidatedChunkOverlap_MoreThan50Percent_ShouldReturnInputWithWarning() {
        // 当 - 分块大小的60%，应该返回输入但记录警告
        int result = document.getValidatedChunkOverlap(300, 500);
        
        // 那么
        assertEquals(300, result); // 应该返回输入（有效但不常见）
    }

    @Test
    void defaultValues_ShouldBeValid() {
        // 那么
        assertTrue(document.getDefaultChunkSize() >= 100);
        assertTrue(document.getDefaultChunkSize() <= 2000);
        assertTrue(document.getDefaultChunkOverlap() >= 10);
        assertTrue(document.getDefaultChunkOverlap() < document.getDefaultChunkSize());
        assertTrue(document.getMaxFileSize() >= 1024);
        assertTrue(document.getBatchSize() >= 10);
        assertTrue(document.getBatchSize() <= 1000);
    }

    @Test
    void chunkOverlapShouldBe10PercentOfDefaultChunkSize() {
        // 那么
        int expectedOverlap = document.getDefaultChunkSize() / 10;
        assertEquals(expectedOverlap, document.getDefaultChunkOverlap());
    }
}