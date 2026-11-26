package com.example.paintquality.excel;

import com.example.paintquality.common.ApiException;
import com.example.paintquality.common.CurrentUser;
import com.example.paintquality.common.RequireRoles;
import com.example.paintquality.user.InspectorUser;
import com.example.paintquality.user.Role;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/template")
@RequireRoles({Role.PRIMARY_ADMIN, Role.SECONDARY_ADMIN})
public class AdminTemplateController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final TemplateService templateService;

    public AdminTemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> upload(@CurrentUser InspectorUser user,
                                       @RequestPart("file") MultipartFile file,
                                       @RequestParam(name = "area", required = false) String area) {
        templateService.replace(resolveArea(user, area), file);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<Resource> download(@CurrentUser InspectorUser user) {
        byte[] content = templateService.effectiveTemplate(user.getCheckArea());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("quality-check-template.xlsx").build().toString())
                .body(new ByteArrayResource(content));
    }

    private String resolveArea(InspectorUser user, String requestedArea) {
        if (user.getRole() == Role.SECONDARY_ADMIN) {
            if (!TemplateService.isSupportedArea(user.getCheckArea())) {
                throw ApiException.badRequest("您的检查区域不是有效的模板区域，请联系一级管理员设置");
            }
            return user.getCheckArea().trim();
        }
        return TemplateService.requireSupportedArea(requestedArea);
    }
}
