package org.e4s.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "e4s")
public class ModelConfigProperties {

    private String modelsPath;
    private int retentionDays = 21;
    private int idleHours = 24;
    private EvictionConfig eviction = new EvictionConfig();

    public String getModelsPath() {
        return modelsPath;
    }

    public void setModelsPath(String modelsPath) {
        this.modelsPath = modelsPath;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public int getIdleHours() {
        return idleHours;
    }

    public void setIdleHours(int idleHours) {
        this.idleHours = idleHours;
    }

    public EvictionConfig getEviction() {
        return eviction;
    }

    public void setEviction(EvictionConfig eviction) {
        this.eviction = eviction;
    }

    public static class EvictionConfig {
        private long intervalMs = 3600000;

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }
    }
}
