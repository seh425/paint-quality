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
    public List<QualityRecordResponse> findShared(LocalDate workDate) {
        LocalDate today = LocalDate.now(clock);
        if (workDate.isAfter(today) || workDate.isBefore(today.minusDays(2))) {
            throw ApiException.badRequest("只能查询今天、昨天或前天的共享记录");
        }
        return findDayEntities(workDate).stream().map(QualityRecordResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<QualityRecord> findDayEntities(LocalDate workDate) {
        return recordRepository.findAllByWorkDateOrderByRoundNumberAscLineNumberAscVehiclePositionAsc(workDate);
    }

    @Transactional
    public List<QualityRecordResponse> saveBatch(RecordBatchRequest request, InspectorUser inspector) {
        LocalDate today = LocalDate.now(clock);
        if (request.workDate().isAfter(today) || request.workDate().isBefore(today.minusDays(1))) {
            throw ApiException.badRequest("只能录入今天或昨天的记录");
        }
        validateVehicles(request.vehicles());

        Map<Integer, QualityRecord> existing = recordRepository
                .findAllByWorkDateAndRoundNumberAndLineNumberOrderByVehiclePositionAsc(
                        request.workDate(), request.roundNumber(), request.lineNumber())
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
                }
                apply(record, input, inspector, models);
                recordRepository.save(record);
            }
            recordRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict("该检查位置已被占用，整批未保存");
        }
        return recordRepository.findAllByWorkDateAndRoundNumberAndLineNumberOrderByVehiclePositionAsc(
                        request.workDate(), request.roundNumber(), request.lineNumber())
                .stream().map(QualityRecordResponse::from).toList();
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
            if (!vin.isEmpty() && !LEGAL_VIN.matcher(vin).matches()) {
                throw ApiException.badRequest("非空VIN必须为合法的17位VIN");
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
        record.setIssueNote(input.status() == InspectionStatus.NOK ? input.issueNote().trim() : null);
        record.setInspector(inspector);
        record.setInspectorName(inspector.getInspectorName());
        record.setArea(inspector.getArea());
    }
}
