package com.makeimage.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String jwtSecret;
    private long jwtExpirationHours;
    private String storageDir;
    private String publicBaseUrl;
    private String thumbnailBaseUrl;

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

    public String getThumbnailBaseUrl() {
        return thumbnailBaseUrl;
    }

    public void setThumbnailBaseUrl(String thumbnailBaseUrl) {
        this.thumbnailBaseUrl = thumbnailBaseUrl;
    }
}
