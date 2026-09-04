package com.careerplatform.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Application-owned switch for opt-in AI usage. */
@ConfigurationProperties(prefix = "career-platform.ai")
public class AiProperties {

    private boolean enabled;
    private String provider = "none";
    private String apiKey;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return enabled && "openai".equalsIgnoreCase(provider) && apiKey != null && !apiKey.isBlank();
    }
}
