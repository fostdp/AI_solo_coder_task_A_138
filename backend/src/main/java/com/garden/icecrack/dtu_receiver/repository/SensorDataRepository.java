package com.garden.icecrack.dtu_receiver.repository;

import com.garden.icecrack.dtu_receiver.entity.SensorData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SensorDataRepository extends JpaRepository<SensorData, Long> {

    List<SensorData> findByPavementIdOrderByRecordedAtDesc(UUID pavementId);

    List<SensorData> findByPavementIdAndRecordedAtBetween(UUID pavementId, LocalDateTime start, LocalDateTime end);
}
