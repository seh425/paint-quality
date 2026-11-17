package com.example.paintquality;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String dataDir = "./data";
    private String zoneId = "Asia/Shanghai";
    private long tokenValidityDays = 90;
    private String cleanupCron = "0 0 1 * * *";
    private final BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();
    private final Feishu feishu = new Feishu();

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

    public Feishu getFeishu() {
        return feishu;
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

    public static class Feishu {
        private String appId = "";
        private String appSecret = "";
        private String defaultReceiverOpenId = "";
        private String baseUrl = "https://open.feishu.cn";

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getDefaultReceiverOpenId() {
            return defaultReceiverOpenId;
        }

        public void setDefaultReceiverOpenId(String defaultReceiverOpenId) {
            this.defaultReceiverOpenId = defaultReceiverOpenId;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
