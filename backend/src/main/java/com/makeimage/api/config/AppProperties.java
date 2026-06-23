package com.makeimage.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String jwtSecret;
    private long jwtExpirationHours;
    private String storageDir;
    private String publicBaseUrl;
    private OpenAiProperties openai = new OpenAiProperties();

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getJwtExpirationHours() {
        return jwtExpirationHours;
    }

    public void setJwtExpirationHours(long jwtExpirationHours) {
        this.jwtExpirationHours = jwtExpirationHours;
    }

    public String getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(String storageDir) {
        this.storageDir = storageDir;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public OpenAiProperties getOpenai() {
        return openai;
    }

    public void setOpenai(OpenAiProperties openai) {
        this.openai = openai;
    }

    public static class OpenAiProperties {
        private boolean enabled;
        private String baseUrl;
        private String apiKey;
        private String model;
        private String reviewModel;
        private String reasoningEffort;
        private boolean disableResponseStorage;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getReviewModel() {
            return reviewModel;
        }

        public void setReviewModel(String reviewModel) {
            this.reviewModel = reviewModel;
        }

        public String getReasoningEffort() {
            return reasoningEffort;
        }

        public void setReasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
        }

        public boolean isDisableResponseStorage() {
            return disableResponseStorage;
        }

        public void setDisableResponseStorage(boolean disableResponseStorage) {
            this.disableResponseStorage = disableResponseStorage;
        }
    }
}
