package com.careerplatform.ai.service;

import com.careerplatform.ai.exception.AiProviderException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class EmbeddingVectors {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private EmbeddingVectors() { }

    public static void validate(float[] vector) {
        if (vector == null || vector.length == 0 || vector.length > 4096) throw invalid();
        double norm = 0;
        for (float v : vector) {
            if (!Float.isFinite(v)) throw invalid();
            norm += (double) v * v;
        }
        if (!(norm > 0) || !Double.isFinite(norm)) throw invalid();
    }

    public static double cosine(float[] a, float[] b) {
        validate(a); validate(b);
        if (a.length != b.length) throw invalid();
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i]; na += (double) a[i] * a[i]; nb += (double) b[i] * b[i];
        }
        return Math.max(-1, Math.min(1, dot / Math.sqrt(na * nb)));
    }

    public static String encode(float[] vector) {
        validate(vector);
        try { return JSON.writeValueAsString(vector); }
        catch (Exception ignored) { throw invalid(); }
    }

    public static float[] decode(String json) {
        if (json == null || json.length() > 100_000) throw invalid();
        try {
            var node = JSON.readTree(json);
            if (!node.isArray() || node.size() == 0 || node.size() > 4096) throw invalid();
            float[] vector = new float[node.size()];
            for (int i = 0; i < node.size(); i++) {
                if (!node.get(i).isNumber()) throw invalid();
                vector[i] = node.get(i).floatValue();
            }
            validate(vector); return vector;
        } catch (Exception ignored) { throw invalid(); }
    }

    private static AiProviderException invalid() { return new AiProviderException("向量数据无法安全处理，请重新索引资料"); }
}
