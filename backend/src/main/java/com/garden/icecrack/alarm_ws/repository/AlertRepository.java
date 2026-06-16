package com.garden.icecrack.alarm_ws.repository;

import com.garden.icecrack.alarm_ws.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByAcknowledgedFalseOrderByCreatedAtDesc();

    List<Alert> findByPavementIdOrderByCreatedAtDesc(UUID pavementId);
}
