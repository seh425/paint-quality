package com.example.paintquality.excel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AreaTemplateRepository extends JpaRepository<AreaTemplate, Long> {

    Optional<AreaTemplate> findByArea(String area);
}
