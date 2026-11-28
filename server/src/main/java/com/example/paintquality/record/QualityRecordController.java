package com.example.paintquality.record;

import com.example.paintquality.common.CurrentUser;
import com.example.paintquality.user.InspectorUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/records")
public class QualityRecordController {

    private final QualityRecordService recordService;

    public QualityRecordController(QualityRecordService recordService) {
        this.recordService = recordService;
    }

    @GetMapping
    public List<QualityRecordResponse> findShared(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String checkArea,
            @CurrentUser InspectorUser user) {
        String target = checkArea == null || checkArea.isBlank()
                ? user.getCheckArea() : checkArea.trim();
        return recordService.findShared(date, target);
    }

    @GetMapping("/dates")
    public List<LocalDate> retainedDates() {
        return recordService.retainedDates();
    }

    @PostMapping("/batches")
    public List<QualityRecordResponse> saveBatch(@Valid @RequestBody RecordBatchRequest request,
                                                  @CurrentUser InspectorUser inspector) {
        return recordService.saveBatch(request, inspector);
    }
}
