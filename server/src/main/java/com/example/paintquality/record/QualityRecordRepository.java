package com.example.paintquality.record;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface QualityRecordRepository extends JpaRepository<QualityRecord, Long> {

    List<QualityRecord> findAllByWorkDateOrderByRoundNumberAscLineNumberAscVehiclePositionAsc(LocalDate workDate);

    List<QualityRecord> findAllByWorkDateAndCheckAreaOrderByRoundNumberAscLineNumberAscVehiclePositionAsc(
            LocalDate workDate, String checkArea);

    List<QualityRecord> findAllByWorkDateAndRoundNumberAndLineNumberOrderByVehiclePositionAsc(
            LocalDate workDate, int roundNumber, int lineNumber);

    List<QualityRecord> findAllByWorkDateAndCheckAreaAndRoundNumberAndLineNumberOrderByVehiclePositionAsc(
            LocalDate workDate, String checkArea, int roundNumber, int lineNumber);

    long deleteByWorkDateBefore(LocalDate cutoff);
}
