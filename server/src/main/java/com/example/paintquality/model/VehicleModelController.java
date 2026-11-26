package com.example.paintquality.model;

import com.example.paintquality.common.RequireRoles;
import com.example.paintquality.user.Role;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/models")
public class VehicleModelController {

    private final VehicleModelService modelService;

    public VehicleModelController(VehicleModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    public List<VehicleModelResponse> findActive() {
        return modelService.findActive();
    }

    @PostMapping
    @RequireRoles({Role.PRIMARY_ADMIN, Role.SECONDARY_ADMIN})
    public VehicleModelResponse create(@Valid @RequestBody VehicleModelRequest request) {
        return modelService.create(request);
    }

    @PutMapping("/{id}")
    @RequireRoles({Role.PRIMARY_ADMIN, Role.SECONDARY_ADMIN})
    public VehicleModelResponse update(@PathVariable Long id,
                                       @Valid @RequestBody VehicleModelRequest request) {
        return modelService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @RequireRoles({Role.PRIMARY_ADMIN, Role.SECONDARY_ADMIN})
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        modelService.delete(id);
        return ResponseEntity.ok().build();
    }
}
