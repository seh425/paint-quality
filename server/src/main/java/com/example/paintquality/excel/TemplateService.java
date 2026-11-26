package com.example.paintquality.excel;

import com.example.paintquality.AppProperties;
import com.example.paintquality.common.ApiException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class TemplateService {

    public static final String SHEET_NAME = "5X5X5";
    public static final List<String> AREAS = List.of(
            "前处理", "电泳", "电泳烘房",
            "密封线", "UBC", "Antichip", "中涂烘房出口",
            "中涂喷房", "中涂烘房", "面漆1线", "面漆2线", "面漆烘房1线", "面漆烘房2线");

    private final AreaTemplateRepository templateRepository;
    private final Path legacyTemplate;

    public TemplateService(AreaTemplateRepository templateRepository, AppProperties properties) {
        this.templateRepository = templateRepository;
        this.legacyTemplate = Paths.get(properties.getDataDir()).toAbsolutePath().normalize()
                .resolve("current-template.xlsx");
    }

    public static boolean isSupportedArea(String area) {
        return area != null && AREAS.contains(area.trim());
    }

    public static String requireSupportedArea(String area) {
        String normalized = area == null ? "" : area.trim();
        if (!AREAS.contains(normalized)) {
            throw ApiException.badRequest("检查区域必须是模板支持的检查区域");
        }
        return normalized;
    }

    @Transactional
    public void replace(String area, MultipartFile file) {
        String target = requireSupportedArea(area);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请上传非空xlsx模板");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw ApiException.badRequest("模板必须是.xlsx文件");
        }
        byte[] content;
        try (InputStream input = file.getInputStream()) {
            content = input.readAllBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "模板保存失败");
        }
        List<RoundInfo> rounds = parseRounds(content, true);
        if (rounds.size() > 5) {
            throw ApiException.badRequest("模板检查轮次不能超过5次");
        }
        AreaTemplate template = templateRepository.findByArea(target)
                .orElseGet(() -> new AreaTemplate(target, "", Instant.now()));
        template.setContentBase64(Base64.getEncoder().encodeToString(content));
        template.setUpdatedAt(Instant.now());
        templateRepository.save(template);
    }

    @Transactional(readOnly = true)
    public List<RoundInfo> rounds(String area) {
        String target = area == null ? "" : area.trim();
        return templateRepository.findByArea(target)
                .map(template -> parseRounds(decode(template.getContentBase64()), false))
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public boolean hasTemplate(String area) {
        return area != null && templateRepository.findByArea(area.trim()).isPresent();
    }

    public byte[] effectiveTemplate(String area) {
        String target = area == null ? "" : area.trim();
        byte[] uploaded = templateRepository.findByArea(target)
                .map(template -> decode(template.getContentBase64()))
                .orElse(null);
        if (uploaded != null) {
            return uploaded;
        }
        if (Files.isRegularFile(legacyTemplate)) {
            try {
                return Files.readAllBytes(legacyTemplate);
            } catch (IOException ignored) {
                // Fall back to the bundled template below.
            }
        }
        return defaultTemplate();
    }

    private byte[] defaultTemplate() {
        ClassPathResource resource = new ClassPathResource("quality-check-template.xlsx");
        if (!resource.exists()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "内置Excel模板不存在");
        }
        try (InputStream input = resource.getInputStream()) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "内置Excel模板读取失败");
        }
    }

    private byte[] decode(String contentBase64) {
        try {
            return Base64.getDecoder().decode(contentBase64 == null ? "" : contentBase64);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "模板数据损坏，请重新上传");
        }
    }

    private List<RoundInfo> parseRounds(byte[] content, boolean strict) {
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (!(workbook instanceof XSSFWorkbook)) {
                throw ApiException.badRequest("上传文件不是有效的xlsx模板");
            }
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                throw ApiException.badRequest("xlsx模板必须包含5X5X5工作表");
            }
            int headerRow = -1;
            int sequenceColumn = -1;
            int timeAreaColumn = -1;
            int headerScanEnd = Math.min(sheet.getLastRowNum(), 20);
            for (int rowIndex = 0; rowIndex <= headerScanEnd && headerRow < 0; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                int sequence = -1;
                int timeArea = -1;
                for (int column = 0; column < row.getLastCellNum(); column++) {
                    String text = text(row, column, formatter);
                    if (sequence < 0 && text.contains("顺序")) {
                        sequence = column;
                    }
                    if (timeArea < 0 && text.contains("时间") && text.contains("责任")) {
                        timeArea = column;
                    }
                }
                if (sequence >= 0 && timeArea >= 0) {
                    headerRow = rowIndex;
                    sequenceColumn = sequence;
                    timeAreaColumn = timeArea;
                }
            }
            if (headerRow < 0) {
                if (strict) {
                    throw ApiException.badRequest("模板中未识别到“顺序”和“时间/责任区域”表头");
                }
                return List.of();
            }
            List<RoundInfo> rounds = new ArrayList<>();
            for (int rowIndex = headerRow + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                String sequence = normalize(text(row, sequenceColumn, formatter));
                String timeArea = normalize(text(row, timeAreaColumn, formatter));
                if (!sequence.isEmpty() && !timeArea.isEmpty()) {
                    rounds.add(new RoundInfo(rounds.size() + 1, sequence, timeArea));
                }
            }
            if (strict && rounds.isEmpty()) {
                throw ApiException.badRequest("模板中未识别到检查轮次，请确认已填写检查顺序和时间/责任区域");
            }
            return rounds;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw ApiException.badRequest("上传文件不是有效的xlsx模板");
        }
    }

    private String text(Row row, int column, DataFormatter formatter) {
        if (column < 0) {
            return "";
        }
        Cell cell = row.getCell(column);
        return cell == null ? "" : formatter.formatCellValue(cell);
    }

    private String normalize(String value) {
        return value.replace('\n', ' ').replace('\r', ' ').replace('\u00A0', ' ')
                .replaceAll("\\s+", " ").trim();
    }
}
