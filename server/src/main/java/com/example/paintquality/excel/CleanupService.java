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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        List<LocalDate> recordedDates = recordRepository.findDistinctWorkDatesDesc();
        LocalDate cutoff = recordedDates.size() > 7 ? recordedDates.get(6) : null;
        long deletedRecords = cutoff == null ? 0 : recordRepository.deleteByWorkDateBefore(cutoff);
        long deletedTokens = tokenRepository.deleteByExpiresAtBefore(Instant.now(clock));
        int deletedFiles = cutoff == null ? 0 : deleteOldExports(cutoff);
        log.info("Daily cleanup kept {} recorded dates, removed {} records, {} expired tokens and {} exports",
                Math.min(recordedDates.size(), 7), deletedRecords, deletedTokens, deletedFiles);
    }

    private int deleteOldExports(LocalDate cutoff) {
        if (!Files.isDirectory(exportDirectory)) {
            return 0;
        }
        int deleted = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(exportDirectory, "*.xlsx")) {
            for (Path file : files) {
                String filename = file.getFileName().toString();
                LocalDate date = extractDate(filename);
                if (date != null && date.isBefore(cutoff)) {
                    Files.deleteIfExists(file);
                    deleted++;
                }
            }
        } catch (IOException exception) {
            log.warn("Failed to clean old Excel exports", exception);
        }
        return deleted;
    }

    private LocalDate extractDate(String filename) {
        Matcher matcher = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(filename);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDate.parse(matcher.group(1));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
