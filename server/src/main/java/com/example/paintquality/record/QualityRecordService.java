package com.example.paintquality.record;

import com.example.paintquality.common.ApiException;
import com.example.paintquality.model.VehicleModel;
import com.example.paintquality.model.VehicleModelRepository;
import com.example.paintquality.user.InspectorUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class QualityRecordService {

    private static final Pattern LEGAL_VIN = Pattern.compile("[A-HJ-NPR-Z0-9]{17}");

    private final QualityRecordRepository recordRepository;
    private final VehicleModelRepository modelRepository;
    private final Clock clock;

    public QualityRecordService(QualityRecordRepository recordRepository,
                                VehicleModelRepository modelRepository, Clock clock) {
        this.recordRepository = recordRepository;
        this.modelRepository = modelRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<QualityRecordResponse> findShared(LocalDate workDate, String checkArea) {
        List<LocalDate> available = retainedDates();
        if (!available.contains(workDate)) {
            throw ApiException.badRequest("只能查询最近7个有检查记录的日期");
        }
        return findDayEntities(workDate, checkArea).stream().map(QualityRecordResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LocalDate> retainedDates() {
        return recordRepository.findDistinctWorkDatesDesc().stream().limit(7).toList();
    }

    @Transactional(readOnly = true)
    public List<QualityRecord> findDayEntities(LocalDate workDate) {
        return findDayEntities(workDate, null);
    }

    @Transactional(readOnly = true)
    public List<QualityRecord> findDayEntities(LocalDate workDate, String checkArea) {
        String target = checkArea == null ? "" : checkArea.trim();
        if (target.isEmpty()) {
            return recordRepository
                    .findAllByWorkDateOrderByRoundNumberAscLineNumberAscVehiclePositionAsc(workDate);
        }
        return recordRepository
                .findAllByWorkDateAndCheckAreaOrderByRoundNumberAscLineNumberAscVehiclePositionAsc(
                        workDate, target);
    }

    @Transactional
    public List<QualityRecordResponse> saveBatch(RecordBatchRequest request, InspectorUser inspector) {
        LocalDate today = LocalDate.now(clock);
        if (request.workDate().isAfter(today) || request.workDate().isBefore(today.minusDays(1))) {
            throw ApiException.badRequest("只能录入今天或昨天的记录");
        }
        validateVehicles(request.vehicles());
        String checkArea = inspector.getCheckArea() == null ? "" : inspector.getCheckArea().trim();

        Map<Integer, QualityRecord> existing = existingRecords(request, checkArea)
                .stream().collect(Collectors.toMap(QualityRecord::getVehiclePosition, Function.identity()));
        Map<String, VehicleModel> models = new HashMap<>();
        LocalDateTime now = LocalDateTime.now(clock);

        try {
            for (VehicleInput input : request.vehicles()) {
                QualityRecord record = existing.get(input.vehiclePosition());
                if (record != null && !record.getInspector().getId().equals(inspector.getId())) {
                    throw ApiException.conflict("该检查位置已由其他检查人占用，整批未保存");
                }
                if (record == null) {
                    record = new QualityRecord();
                    record.setWorkDate(request.workDate());
                    record.setRoundNumber(request.roundNumber());
                    record.setLineNumber(request.lineNumber());
                    record.setVehiclePosition(input.vehiclePosition());
                    record.setCreatedAt(now);
                    apply(record, input, inspector, models);
                } else if (request.doubleCheck()) {
                    appendDoubleCheck(record, input, models);
                } else {
                    apply(record, input, inspector, models);
                }
                recordRepository.save(record);
            }
            recordRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict("该检查位置已被占用，整批未保存");
        }
        return existingRecords(request, checkArea).stream()
                .map(QualityRecordResponse::from).toList();
    }

    private List<QualityRecord> existingRecords(RecordBatchRequest request, String checkArea) {
        if (checkArea.isEmpty()) {
            return recordRepository
                    .findAllByWorkDateAndRoundNumberAndLineNumberOrderByVehiclePositionAsc(
                            request.workDate(), request.roundNumber(), request.lineNumber());
        }
        return recordRepository
                .findAllByWorkDateAndCheckAreaAndRoundNumberAndLineNumberOrderByVehiclePositionAsc(
                        request.workDate(), checkArea, request.roundNumber(), request.lineNumber());
    }

    private void validateVehicles(List<VehicleInput> inputs) {
        Set<Integer> positions = new HashSet<>();
        for (VehicleInput input : inputs) {
            if (!positions.add(input.vehiclePosition())) {
                throw ApiException.badRequest("每批车辆位置必须恰好覆盖1至5且不可重复");
            }
            if (input.status() == InspectionStatus.NOK
                    && (input.issueNote() == null || input.issueNote().isBlank())) {
                throw ApiException.badRequest("NOK状态必须填写问题备注");
            }
            if (input.status() != InspectionStatus.NOK
                    && input.issueNote() != null && !input.issueNote().isBlank()) {
                throw ApiException.badRequest("NA/OK状态不可填写问题备注");
            }
            String vin = input.vin().trim().toUpperCase(Locale.ROOT);
            String vehicleNumber = input.vehicleNumber().trim().toUpperCase(Locale.ROOT);
            if (!vin.isEmpty()) {
                if (!LEGAL_VIN.matcher(vin).matches()) {
                    throw ApiException.badRequest("非空VIN必须为合法的17位VIN");
                }
                if (!vin.substring(vin.length() - 6).equals(vehicleNumber)) {
                    throw ApiException.badRequest("合法17位VIN的车号必须为VIN后6位");
                }
            }
        }
        if (positions.size() != 5 || !positions.containsAll(Set.of(1, 2, 3, 4, 5))) {
            throw ApiException.badRequest("每批车辆位置必须恰好覆盖1至5且不可重复");
        }
    }

    private void apply(QualityRecord record, VehicleInput input, InspectorUser inspector,
                       Map<String, VehicleModel> models) {
        String modelCode = input.modelCode().trim().toUpperCase(Locale.ROOT);
        VehicleModel model = models.computeIfAbsent(modelCode, code -> modelRepository
                .findByCodeAndActiveTrue(code)
                .orElseThrow(() -> ApiException.badRequest("车型代码不存在或已停用: " + code)));

        record.setVin(input.vin().trim().toUpperCase(Locale.ROOT));
        record.setVehicleNumber(input.vehicleNumber().trim().toUpperCase(Locale.ROOT));
        record.setModelCode(model.getCode());
        record.setVehicleModel(input.vehicleModel().trim());
        record.setStatus(input.status());
        record.setStatusText(input.status().name());
        record.setIssueNote(input.status() == InspectionStatus.NOK ? input.issueNote().trim() : null);
        record.setInspector(inspector);
        record.setInspectorName(inspector.getInspectorName());
        record.setArea(inspector.getArea());
        record.setCheckArea(inspector.getCheckArea() == null || inspector.getCheckArea().isBlank()
                ? null : inspector.getCheckArea().trim());
    }

    private void appendDoubleCheck(QualityRecord record, VehicleInput input,
                                   Map<String, VehicleModel> models) {
        String modelCode = input.modelCode().trim().toUpperCase(Locale.ROOT);
        models.computeIfAbsent(modelCode, code -> modelRepository
                .findByCodeAndActiveTrue(code)
                .orElseThrow(() -> ApiException.badRequest("车型代码不存在或已停用: " + code)));

        record.setVehicleNumber(appendValue(record.getVehicleNumber(),
                input.vehicleNumber().trim().toUpperCase(Locale.ROOT)));
        record.setVehicleModel(appendValue(record.getVehicleModel(), input.vehicleModel().trim()));
        String firstStatus = record.getStatusText() == null || record.getStatusText().isBlank()
                ? (record.getStatus() == null ? "" : record.getStatus().name())
                : record.getStatusText();
        record.setStatusText(appendValue(firstStatus, input.status().name()));
        if (input.status() == InspectionStatus.NOK) {
            record.setIssueNote(appendValue(record.getIssueNote(), input.issueNote().trim()));
        }
    }

    private String appendValue(String existing, String addition) {
        String left = existing == null ? "" : existing.trim();
        String right = addition == null ? "" : addition.trim();
        if (right.isEmpty()) {
            return left;
        }
        if (left.isEmpty()) {
            return right;
        }
        for (String part : left.split("、")) {
            if (part.trim().equalsIgnoreCase(right)) {
                return left;
            }
        }
        return left + "、" + right;
    }
}
