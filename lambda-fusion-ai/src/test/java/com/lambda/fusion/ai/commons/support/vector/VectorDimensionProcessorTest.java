package com.lambda.fusion.ai.commons.support.vector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VectorDimensionProcessorTest {

    private final VectorDimensionProcessor processor = new VectorDimensionProcessor();

    @Test
    @DisplayName("非标准维度向上选择支持维度避免截断")
    void testGetNearestSupportedDimensionUsesCeilingDimension() {
        assertThat(processor.getNearestSupportedDimension(1024)).isEqualTo(1536);
        assertThat(processor.getNearestSupportedDimension(2000)).isEqualTo(3072);
        assertThat(processor.getNearestSupportedDimension(3500)).isEqualTo(4096);
    }

    @Test
    @DisplayName("边界维度保持原有结果")
    void testGetNearestSupportedDimensionBoundaryValues() {
        assertThat(processor.getNearestSupportedDimension(768)).isEqualTo(768);
        assertThat(processor.getNearestSupportedDimension(0)).isEqualTo(VectorDimensionProcessor.DEFAULT_DIMENSION);
        assertThat(processor.getNearestSupportedDimension(5000)).isEqualTo(VectorDimensionProcessor.MAX_DIMENSION);
    }
}
