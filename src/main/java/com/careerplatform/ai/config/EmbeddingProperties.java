package com.careerplatform.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;

@ConfigurationProperties(prefix = "career-platform.embedding")
public class EmbeddingProperties {
    private boolean enabled;
    private String endpoint = "";
    private String model = "";
    private String version = "1";
    private String apiKey = "";
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String value) { endpoint = value; }
    public String getModel() { return model; }
    public void setModel(String value) { model = value; }
    public String getVersion() { return version; }
    public void setVersion(String value) { version = value; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String value) { apiKey = value; }
    public boolean isConfigured() {
        if (!enabled || apiKey == null || apiKey.isBlank() || model == null || model.isBlank()
                || model.length() > 150 || version == null || version.isBlank()) return false;
        try {
            URI uri = URI.create(endpoint);
            return "https".equals(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null;
        } catch (RuntimeException ignored) { return false; }
    }
}
