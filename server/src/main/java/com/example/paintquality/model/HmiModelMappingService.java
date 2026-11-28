package com.example.paintquality.model;

import com.example.paintquality.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class HmiModelMappingService {

    private final HmiModelMappingRepository mappingRepository;
    private final VehicleModelRepository modelRepository;

    public HmiModelMappingService(HmiModelMappingRepository mappingRepository,
                                  VehicleModelRepository modelRepository) {
        this.mappingRepository = mappingRepository;
        this.modelRepository = modelRepository;
    }

    @Transactional(readOnly = true)
    public List<HmiModelMappingResponse> findAll() {
        return mappingRepository.findAllByOrderByHmiValueAsc().stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public HmiModelMappingResponse create(HmiModelMappingRequest request) {
        String hmiValue = normalize(request.hmiValue());
        String pviCode = normalize(request.pviCode());
        validateTargetModel(pviCode);
        if (mappingRepository.existsByHmiValueIgnoreCase(hmiValue)) {
            throw ApiException.conflict("HMI车型代码已存在: " + hmiValue);
        }
        HmiModelMapping mapping = mappingRepository.save(new HmiModelMapping(hmiValue, pviCode));
        return toResponse(mapping);
    }

    @Transactional
    public HmiModelMappingResponse update(Long id, HmiModelMappingRequest request) {
        HmiModelMapping mapping = mappingRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("HMI车型转化不存在"));
        String hmiValue = normalize(request.hmiValue());
        String pviCode = normalize(request.pviCode());
        validateTargetModel(pviCode);
        if (mappingRepository.existsByHmiValueIgnoreCaseAndIdNot(hmiValue, id)) {
            throw ApiException.conflict("HMI车型代码已存在: " + hmiValue);
        }
        mapping.setHmiValue(hmiValue);
        mapping.setPviCode(pviCode);
        return toResponse(mappingRepository.save(mapping));
    }

    @Transactional
    public void delete(Long id) {
        if (!mappingRepository.existsById(id)) {
            throw ApiException.notFound("HMI车型转化不存在");
        }
        mappingRepository.deleteById(id);
    }

    private void validateTargetModel(String pviCode) {
        modelRepository.findByCodeAndActiveTrue(pviCode)
                .orElseThrow(() -> ApiException.badRequest("目标PVI车型代码不存在或已停用: " + pviCode));
    }

    private HmiModelMappingResponse toResponse(HmiModelMapping mapping) {
        String modelName = modelRepository.findByCodeAndActiveTrue(mapping.getPviCode())
                .map(VehicleModel::getModelName).orElse("");
        return new HmiModelMappingResponse(mapping.getId(), mapping.getHmiValue(),
                mapping.getPviCode(), modelName);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
