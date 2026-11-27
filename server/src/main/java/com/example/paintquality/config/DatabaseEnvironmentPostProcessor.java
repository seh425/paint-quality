package com.example.paintquality.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * 兼容 Render/Supabase 常见连接串格式，并输出脱敏后的数据库连接信息。
 */
public class DatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Log log = LogFactory.getLog(DatabaseEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        String configured = environment.getProperty("spring.datasource.url");
        if (configured == null || configured.isBlank()) {
            configured = environment.getProperty("DB_URL");
        }
        if (configured == null || configured.isBlank()) {
            String dataDir = environment.getProperty("APP_DATA_DIR", "./data");
            configured = "jdbc:h2:file:" + dataDir + "/paint-quality;DB_CLOSE_ON_EXIT=FALSE";
        }
        String normalized = normalize(configured);
        Map<String, Object> values = new HashMap<>();
        values.put("spring.datasource.url", normalized);
        environment.getPropertySources()
                .addFirst(new MapPropertySource("normalizedDataSource", values));
        log.info("数据库连接地址：" + sanitize(normalized));
    }

    private String normalize(String value) {
        String url = value.trim();
        if (url.length() >= 2 && url.startsWith("\"") && url.endsWith("\"")) {
            url = url.substring(1, url.length() - 1).trim();
        }
        if (url.startsWith("postgres://")) {
            return "jdbc:postgresql://" + url.substring("postgres://".length());
        }
        if (url.startsWith("postgresql://")) {
            return "jdbc:postgresql://" + url.substring("postgresql://".length());
        }
        return url;
    }

    private String sanitize(String url) {
        return url.replaceAll("//[^/@]+@", "//***@");
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
