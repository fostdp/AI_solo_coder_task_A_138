package com.garden.icecrack.repository;

import com.garden.icecrack.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByAcknowledgedFalseOrderByCreatedAtDesc();

    List<Alert> findByPavementIdOrderByCreatedAtDesc(UUID pavementId);
}
