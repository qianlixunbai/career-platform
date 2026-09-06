package com.careerplatform.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Opt-in configuration for the fixed Tavily job-search provider. */
@ConfigurationProperties(prefix = "career-platform.job-search")
public class JobSearchProperties {

    private boolean enabled;
    private String apiKey;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Whether a paid provider call is currently allowed.
     *
     * <p>The switch and credential are checked at call time as well as during
     * bean construction, so changing local run configuration cannot cause a
     * partially configured provider call.</p>
     */
    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
