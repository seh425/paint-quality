package com.example.paintquality.excel;

import com.example.paintquality.common.ApiException;
import com.example.paintquality.common.CurrentUser;
import com.example.paintquality.common.RequireRoles;
import com.example.paintquality.user.InspectorUser;
import com.example.paintquality.user.Role;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/excel")
public class ExcelController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExcelService excelService;

    public ExcelController(ExcelService excelService) {
        this.excelService = excelService;
    }

    @GetMapping("/dates")
    public List<LocalDate> dates() {
        return excelService.availableDates();
    }

    @GetMapping("/areas")
    @RequireRoles({Role.PRIMARY_ADMIN, Role.SECONDARY_ADMIN})
    public List<String> areas(@CurrentUser InspectorUser user) {
        if (user.getRole() == Role.PRIMARY_ADMIN) {
            return TemplateService.AREAS;
        }
        List<String> allowed = AreaCatalog.checkAreas(user.getArea());
        if (!allowed.isEmpty()) {
            return allowed;
        }
        return TemplateService.isSupportedArea(user.getCheckArea())
                ? List.of(user.getCheckArea().trim()) : List.of();
    }

    @GetMapping("/{date}.xlsx")
    @RequireRoles({Role.PRIMARY_ADMIN, Role.SECONDARY_ADMIN})
    public ResponseEntity<Resource> download(@CurrentUser InspectorUser user,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "checkArea", required = false) String checkArea) {
        String area = checkArea == null || checkArea.isBlank()
                ? user.getCheckArea() : checkArea.trim();
        if (area == null) {
            area = "";
        }
        if (!area.isEmpty()) {
            if (!TemplateService.isSupportedArea(area)) {
                throw ApiException.badRequest("检查区域必须是模板支持的检查区域");
            }
            if (user.getRole() != Role.PRIMARY_ADMIN) {
                List<String> allowed = AreaCatalog.checkAreas(user.getArea());
                boolean ownArea = area.equals(user.getCheckArea());
                if (!allowed.isEmpty() ? !allowed.contains(area) : !ownArea) {
                    throw ApiException.forbidden("只能导出当前责任区域下的检查区域");
                }
            }
        }
        Path workbook = excelService.generate(date, area);
        String fileName = area.isEmpty()
                ? "quality-check-" + date + ".xlsx" : area + "-" + date + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(workbook));
    }
}
