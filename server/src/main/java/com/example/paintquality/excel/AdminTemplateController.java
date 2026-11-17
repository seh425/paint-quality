package com.example.paintquality.excel;

import com.example.paintquality.common.RequireRoles;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/template")
@RequireRoles(Role.PRIMARY_ADMIN)
public class AdminTemplateController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final TemplateService templateService;

    public AdminTemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> upload(@RequestPart("file") MultipartFile file) {
        templateService.replace(file);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<Resource> download() {
        Resource resource = new ByteArrayResource(templateService.currentContent());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("quality-check-template.xlsx").build().toString())
                .body(resource);
    }
}
