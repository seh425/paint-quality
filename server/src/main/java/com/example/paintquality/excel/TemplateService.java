package com.example.paintquality.excel;

import com.example.paintquality.common.ApiException;
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
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class TemplateService {

    public static final String SHEET_NAME = "5X5X5";

    private final TemplateContentRepository repository;

    public TemplateService(TemplateContentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public byte[] currentContent() {
        return repository.findById(TemplateContent.SINGLETON_ID)
                .map(TemplateContent::getContent)
                .orElseGet(TemplateService::defaultContent);
    }

    @Transactional
    public void replace(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请上传非空xlsx模板");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw ApiException.badRequest("模板必须是.xlsx文件");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "模板读取失败");
        }
        validate(content);
        TemplateContent template = repository.findById(TemplateContent.SINGLETON_ID)
                .orElseGet(() -> new TemplateContent(TemplateContent.SINGLETON_ID, content, LocalDateTime.now()));
        template.setContent(content);
        template.setUpdatedAt(LocalDateTime.now());
        repository.save(template);
    }

    private void validate(byte[] content) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (!(workbook instanceof XSSFWorkbook) || workbook.getSheet(SHEET_NAME) == null) {
                throw ApiException.badRequest("xlsx模板必须包含5X5X5工作表");
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw ApiException.badRequest("上传文件不是有效的xlsx模板");
        }
    }

    private static byte[] defaultContent() {
        ClassPathResource resource = new ClassPathResource("quality-check-template.xlsx");
        if (!resource.exists()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "内置初始模板不存在");
        }
        try (InputStream input = resource.getInputStream()) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "内置初始模板读取失败");
        }
    }
}
