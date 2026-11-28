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
@RequestMapping("/api/hmi-models")
public class HmiModelMappingController {

    private final HmiModelMappingService mappingService;

    public HmiModelMappingController(HmiModelMappingService mappingService) {
        this.mappingService = mappingService;
    }

    @GetMapping
    public List<HmiModelMappingResponse> findAll() {
        return mappingService.findAll();
    }

    @PostMapping
    @RequireRoles({Role.PRIMARY_ADMIN, Role.SECONDARY_ADMIN})
    public HmiModelMappingResponse create(@Valid @RequestBody HmiModelMappingRequest request) {
        return mappingService.create(request);
    }

    @PutMapping("/{id}")
    @RequireRoles({Role.PRIMARY_ADMIN, Role.SECONDARY_ADMIN})
    public HmiModelMappingResponse update(@PathVariable Long id,
                                          @Valid @RequestBody HmiModelMappingRequest request) {
        return mappingService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @RequireRoles({Role.PRIMARY_ADMIN, Role.SECONDARY_ADMIN})
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        mappingService.delete(id);
        return ResponseEntity.ok().build();
    }
}
