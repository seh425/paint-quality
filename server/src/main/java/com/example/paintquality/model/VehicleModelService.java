package com.example.paintquality.model;

import com.example.paintquality.common.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class VehicleModelService {

    private final VehicleModelRepository modelRepository;

    public VehicleModelService(VehicleModelRepository modelRepository) {
        this.modelRepository = modelRepository;
    }

    @Transactional(readOnly = true)
    public List<VehicleModelResponse> findActive() {
        return modelRepository.findAllByActiveTrueOrderByCodeAsc().stream()
                .map(VehicleModelResponse::from).toList();
    }

    @Transactional
    public VehicleModelResponse create(VehicleModelRequest request) {
        String code = normalizeCode(request.code());
        String name = normalizeName(request.modelName());
        if (modelRepository.existsByCode(code)) {
            throw ApiException.conflict("车型代码已存在");
        }
        try {
            VehicleModel model = new VehicleModel(code, name, true);
            return VehicleModelResponse.from(modelRepository.saveAndFlush(model));
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict("车型代码已存在");
        }
    }

    @Transactional
    public VehicleModelResponse update(Long id, VehicleModelRequest request) {
        VehicleModel model = modelRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("车型不存在"));
        String code = normalizeCode(request.code());
        String name = normalizeName(request.modelName());
        if (modelRepository.existsByCodeAndIdNot(code, id)) {
            throw ApiException.conflict("车型代码已存在");
        }
        model.setCode(code);
        model.setModelName(name);
        try {
            return VehicleModelResponse.from(modelRepository.saveAndFlush(model));
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict("车型代码已存在");
        }
    }

    @Transactional
    public void delete(Long id) {
        VehicleModel model = modelRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("车型不存在"));
        modelRepository.delete(model);
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("车型代码不能为空");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("车型名称不能为空");
        }
        return value.trim();
    }
}
