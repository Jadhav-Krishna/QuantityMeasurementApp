package com.app.measurementservice.repository;

import com.app.measurementservice.entity.MeasurementRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeasurementRecordRepository extends JpaRepository<MeasurementRecord, Long> {
    List<MeasurementRecord> findByUserIdOrderByRecordedAtDesc(Long userId);
    List<MeasurementRecord> findAllByOrderByRecordedAtDesc();
}
