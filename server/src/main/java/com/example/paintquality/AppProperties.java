package com.example.paintquality;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String dataDir = "./data";
    private String zoneId = "Asia/Shanghai";
    private long tokenValidityDays = 90;
    private String cleanupCron = "0 0 1 * * *";
    private final BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public long getTokenValidityDays() {
        return tokenValidityDays;
    }

    public void setTokenValidityDays(long tokenValidityDays) {
        this.tokenValidityDays = tokenValidityDays;
    }

    public String getCleanupCron() {
        return cleanupCron;
    }

    public void setCleanupCron(String cleanupCron) {
        this.cleanupCron = cleanupCron;
    }

    public BootstrapAdmin getBootstrapAdmin() {
        return bootstrapAdmin;
    }

    public static class BootstrapAdmin {
        private String name = "admin";
        private String area = "ADMIN";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getArea() {
            return area;
        }

        public void setArea(String area) {
            this.area = area;
        }
    }
}
