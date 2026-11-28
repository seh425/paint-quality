package com.example.paintquality.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HmiModelMappingRepository extends JpaRepository<HmiModelMapping, Long> {

    List<HmiModelMapping> findAllByOrderByHmiValueAsc();

    Optional<HmiModelMapping> findByHmiValueIgnoreCase(String hmiValue);

    boolean existsByHmiValueIgnoreCase(String hmiValue);

    boolean existsByHmiValueIgnoreCaseAndIdNot(String hmiValue, Long id);
}
