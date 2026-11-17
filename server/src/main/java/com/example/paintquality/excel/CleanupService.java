package com.example.paintquality.excel;

import com.example.paintquality.AppProperties;
import com.example.paintquality.auth.AuthTokenRepository;
import com.example.paintquality.record.QualityRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Service
public class CleanupService {

    private static final Logger log = LoggerFactory.getLogger(CleanupService.class);

    private final QualityRecordRepository recordRepository;
    private final AuthTokenRepository tokenRepository;
    private final Clock clock;
    private final Path exportDirectory;

    public CleanupService(QualityRecordRepository recordRepository, AuthTokenRepository tokenRepository,
                          AppProperties properties, Clock clock) {
        this.recordRepository = recordRepository;
        this.tokenRepository = tokenRepository;
        this.clock = clock;
        this.exportDirectory = Paths.get(properties.getDataDir()).toAbsolutePath().normalize()
                .resolve("exports");
    }

    @Scheduled(cron = "${app.cleanup-cron:0 0 1 * * *}", zone = "${app.zone-id:Asia/Shanghai}")
    @Transactional
    public void cleanOldData() {
        LocalDate cutoff = LocalDate.now(clock).minusDays(2);
        long deletedRecords = recordRepository.deleteByWorkDateBefore(cutoff);
        long deletedTokens = tokenRepository.deleteByExpiresAtBefore(Instant.now(clock));
        int deletedFiles = deleteOldExports(cutoff);
        log.info("Daily cleanup removed {} records, {} expired tokens and {} exports",
                deletedRecords, deletedTokens, deletedFiles);
    }

    private int deleteOldExports(LocalDate cutoff) {
        if (!Files.isDirectory(exportDirectory)) {
            return 0;
        }
        int deleted = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(exportDirectory, "*.xlsx")) {
            for (Path file : files) {
                String filename = file.getFileName().toString();
                try {
                    LocalDate date = LocalDate.parse(filename.substring(0, filename.length() - 5));
                    if (date.isBefore(cutoff)) {
                        Files.deleteIfExists(file);
                        deleted++;
                    }
                } catch (DateTimeParseException ignored) {
                    // Only files named yyyy-MM-dd.xlsx are managed exports.
                }
            }
        } catch (IOException exception) {
            log.warn("Failed to clean old Excel exports", exception);
        }
        return deleted;
    }
}
