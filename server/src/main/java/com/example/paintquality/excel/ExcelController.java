package com.example.paintquality.excel;

import com.example.paintquality.common.CurrentUser;
import com.example.paintquality.common.RequireRoles;
import com.example.paintquality.feishu.FeishuService;
import com.example.paintquality.user.InspectorUser;
import com.example.paintquality.user.Role;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/excel")
public class ExcelController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExcelService excelService;
    private final FeishuService feishuService;

    public ExcelController(ExcelService excelService, FeishuService feishuService) {
        this.excelService = excelService;
        this.feishuService = feishuService;
    }

    @GetMapping("/dates")
    public List<LocalDate> dates() {
        return excelService.availableDates();
    }

    @GetMapping("/{date}.xlsx")
    @RequireRoles({Role.PRIMARY_ADMIN, Role.SECONDARY_ADMIN})
    public ResponseEntity<Resource> download(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Path workbook = excelService.generate(date);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("quality-check-" + date + ".xlsx").build().toString())
                .body(new FileSystemResource(workbook));
    }

    @PostMapping("/feishu")
    @RequireRoles({Role.PRIMARY_ADMIN, Role.SECONDARY_ADMIN})
    public Map<String, String> sendToFeishu(@CurrentUser InspectorUser user,
                                            @Valid @RequestBody FeishuSendRequest request) {
        Path workbook = excelService.generate(request.date());
        feishuService.sendWorkbook(user, workbook, request.date());
        return Map.of("date", request.date().toString(), "status", "SENT");
    }
}
