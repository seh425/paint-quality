package com.example.paintquality.excel;

import com.example.paintquality.AppProperties;
import com.example.paintquality.common.ApiException;
import com.example.paintquality.record.InspectionStatus;
import com.example.paintquality.record.QualityRecord;
import com.example.paintquality.record.QualityRecordService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class ExcelService {

    private static final Logger log = LoggerFactory.getLogger(ExcelService.class);
    private static final int[] ROUND_START_ROWS = {6, 11, 16, 21, 26};

    private final QualityRecordService recordService;
    private final TemplateService templateService;
    private final Clock clock;
    private final Path exportDirectory;

    public ExcelService(QualityRecordService recordService, TemplateService templateService,
                        AppProperties properties, Clock clock) {
        this.recordService = recordService;
        this.templateService = templateService;
        this.clock = clock;
        this.exportDirectory = Paths.get(properties.getDataDir()).toAbsolutePath().normalize()
                .resolve("exports");
    }

    public List<LocalDate> availableDates() {
        return recordService.retainedDates();
    }

    public Path generate(LocalDate workDate, String area) {
        validateDate(workDate);
        List<QualityRecord> records = recordService.findDayEntities(workDate, area);
        Path temporary = null;
        try {
            Files.createDirectories(exportDirectory);
            Path target = exportDirectory.resolve(fileName(workDate, area));
            temporary = Files.createTempFile(exportDirectory, workDate + "-", ".xlsx");
            try (InputStream input = new ByteArrayInputStream(templateService.effectiveTemplate(area));
                 Workbook workbook = WorkbookFactory.create(input)) {
                Sheet sheet = workbook.getSheet(TemplateService.SHEET_NAME);
                if (sheet == null) {
                    throw ApiException.badRequest("当前模板缺少5X5X5工作表");
                }
                clearEntryCells(sheet);
                cell(sheet, 3, 1).setCellValue(workDate.toString());
                for (QualityRecord record : records) {
                    writeRecord(workbook, sheet, record);
                }
                writeSignatures(sheet, records);
                try (OutputStream output = Files.newOutputStream(temporary)) {
                    workbook.write(output);
                }
            }
            moveReplacing(temporary, target);
            return target;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to generate Excel for {}", workDate, exception);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Excel生成失败");
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Best-effort temporary file cleanup.
                }
            }
        }
    }

    private String fileName(LocalDate workDate, String area) {
        String target = area == null ? "" : area.trim();
        return target.isEmpty() ? workDate + ".xlsx" : target + "-" + workDate + ".xlsx";
    }

    private void validateDate(LocalDate workDate) {
        if (!availableDates().contains(workDate)) {
            throw ApiException.badRequest("只能导出最近7个有检查记录的日期");
        }
    }

    private void clearEntryCells(Sheet sheet) {
        for (int startRow : ROUND_START_ROWS) {
            for (int offset = 0; offset < 5; offset++) {
                for (int column : new int[]{3, 4, 5, 6, 7, 8}) {
                    Cell cell = cell(sheet, startRow + offset, column);
                    cell.removeCellComment();
                    cell.setBlank();
                }
            }
            cell(sheet, startRow, 11).setBlank();
        }
    }

    private void writeRecord(Workbook workbook, Sheet sheet, QualityRecord record) {
        int rowNumber = ROUND_START_ROWS[record.getRoundNumber() - 1] + record.getVehiclePosition() - 1;
        int firstColumn = record.getLineNumber() == 1 ? 3 : 6;
        cell(sheet, rowNumber, firstColumn).setCellValue(record.getVehicleNumber());
        cell(sheet, rowNumber, firstColumn + 1).setCellValue(record.getVehicleModel());
        Cell statusCell = cell(sheet, rowNumber, firstColumn + 2);
        statusCell.removeCellComment();
        String statusText = record.getStatusText();
        if (statusText == null || statusText.isBlank()) {
            statusText = record.getStatus() == null ? "" : record.getStatus().name();
        }
        statusCell.setCellValue(statusText);
        if (statusText.contains("NOK") && record.getIssueNote() != null
                && !record.getIssueNote().isBlank()) {
            CreationHelper helper = workbook.getCreationHelper();
            ClientAnchor anchor = helper.createClientAnchor();
            anchor.setCol1(statusCell.getColumnIndex());
            anchor.setCol2(statusCell.getColumnIndex() + 2);
            anchor.setRow1(statusCell.getRowIndex());
            anchor.setRow2(statusCell.getRowIndex() + 3);
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            Comment comment = drawing.createCellComment(anchor);
            comment.setString(helper.createRichTextString(record.getIssueNote()));
            comment.setAuthor("质量检查");
            statusCell.setCellComment(comment);
        }
    }

    private void writeSignatures(Sheet sheet, List<QualityRecord> records) {
        for (int round = 1; round <= 5; round++) {
            final int roundNumber = round;
            Set<String> inspectors = records.stream()
                    .filter(record -> record.getRoundNumber() == roundNumber)
                    .map(QualityRecord::getInspectorName)
                    .collect(Collectors.toCollection(TreeSet::new));
            cell(sheet, ROUND_START_ROWS[round - 1], 11)
                    .setCellValue(String.join("、", inspectors));
        }
    }

    private Cell cell(Sheet sheet, int rowNumber, int columnNumber) {
        Row row = sheet.getRow(rowNumber - 1);
        if (row == null) {
            row = sheet.createRow(rowNumber - 1);
        }
        Cell cell = row.getCell(columnNumber - 1);
        return cell == null ? row.createCell(columnNumber - 1) : cell;
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
