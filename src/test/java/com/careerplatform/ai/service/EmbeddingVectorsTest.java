package com.careerplatform.ai.service;

import com.careerplatform.ai.exception.AiProviderException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EmbeddingVectorsTest {
    @Test void cosineAndJsonRoundTrip() {
        float[] vector = {1, 2, 3};
        assertThat(EmbeddingVectors.decode(EmbeddingVectors.encode(vector))).containsExactly(vector);
        assertThat(EmbeddingVectors.cosine(vector, vector)).isCloseTo(1, within(1e-8));
        assertThat(EmbeddingVectors.cosine(new float[]{1, 0}, new float[]{0, 1})).isZero();
        assertThat(EmbeddingVectors.cosine(new float[]{1, 0}, new float[]{-1, 0})).isEqualTo(-1);
    }
    @Test void rejectsMalformedAndZeroVectors() {
        for (float[] bad : new float[][]{null, {}, {0, 0}, {Float.NaN}, {Float.POSITIVE_INFINITY}, new float[4097]})
            assertThatThrownBy(() -> EmbeddingVectors.validate(bad)).isInstanceOf(AiProviderException.class);
        for (String bad : new String[]{"null", "[]", "[0]", "[null,1]", "[\"1\"]", "{\"x\":1}", "not-json"})
            assertThatThrownBy(() -> EmbeddingVectors.decode(bad)).isInstanceOf(AiProviderException.class);
    }
    @Test void rejectsDimensionMismatch() {
        assertThatThrownBy(() -> EmbeddingVectors.cosine(new float[]{1}, new float[]{1, 2}))
                .isInstanceOf(AiProviderException.class);
    }
}
