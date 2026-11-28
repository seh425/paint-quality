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
        alterColumn("vehicle_number", "VARCHAR(64)");
        alterColumn("vehicle_model", "VARCHAR(255)");
        alterColumn("status", "VARCHAR(16)");
        alterColumn("issue_note", "VARCHAR(2000)");
        try {
            jdbcTemplate.execute("ALTER TABLE quality_records ADD COLUMN IF NOT EXISTS status_text VARCHAR(64)");
        } catch (RuntimeException exception) {
            log.warn("无法新增 status_text 字段: {}", exception.getMessage());
        }
    }

    private void alterColumn(String column, String type) {
        try {
            jdbcTemplate.execute("ALTER TABLE quality_records ALTER COLUMN " + column + " TYPE " + type);
        } catch (RuntimeException exception) {
            log.warn("无法调整 quality_records.{} 字段长度: {}", column, exception.getMessage());
        }
    }
}
