package com.example.paintquality.record;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RecordSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecordSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public RecordSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE quality_records DROP CONSTRAINT IF EXISTS uk_quality_record_slot");
        } catch (RuntimeException exception) {
            log.warn("无法移除旧记录唯一约束: {}", exception.getMessage());
        }
        try {
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS uk_quality_record_slot_v2 ON quality_records "
                            + "(work_date, check_area, round_number, line_number, vehicle_position)");
        } catch (RuntimeException exception) {
            log.warn("无法创建记录唯一索引: {}", exception.getMessage());
        }
    }
}
