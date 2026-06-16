package com.garden.icecrack.service;

import com.garden.icecrack.dto.AlertDTO;
import com.garden.icecrack.entity.Alert;
import com.garden.icecrack.entity.Pavement;
import com.garden.icecrack.repository.AlertRepository;
import com.garden.icecrack.repository.PavementRepository;
import com.garden.icecrack.websocket.AlertWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertWebSocketHandler alertWebSocketHandler;
    private final PavementRepository pavementRepository;

    public AlertDTO createAlert(UUID pavementId, String alertType, String severity, String message,
                                double waterDepthMm, double recessionTimeSec) {
        Pavement pavement = pavementRepository.findById(pavementId)
                .orElseThrow(() -> new RuntimeException("Pavement not found"));
        Alert alert = new Alert();
        alert.setPavement(pavement);
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setMessage(message);
        alert.setWaterDepthMm(waterDepthMm);
        alert.setRecessionTimeSec(recessionTimeSec);
        alert.setAcknowledged(false);
        alert.setCreatedAt(LocalDateTime.now());
        Alert saved = alertRepository.save(alert);
        AlertDTO dto = toDTO(saved);
        alertWebSocketHandler.broadcastAlert(dto);
        return dto;
    }

    public List<AlertDTO> getUnacknowledgedAlerts() {
        return alertRepository.findByAcknowledgedFalseOrderByCreatedAtDesc()
                .stream().map(this::toDTO).toList();
    }

    public List<AlertDTO> getAlertsByPavement(UUID pavementId) {
        return alertRepository.findByPavementIdOrderByCreatedAtDesc(pavementId)
                .stream().map(this::toDTO).toList();
    }

    public AlertDTO acknowledgeAlert(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found"));
        alert.setAcknowledged(true);
        alert.setResolvedAt(LocalDateTime.now());
        Alert saved = alertRepository.save(alert);
        return toDTO(saved);
    }

    private AlertDTO toDTO(Alert entity) {
        AlertDTO dto = new AlertDTO();
        dto.setId(entity.getId());
        dto.setPavementId(entity.getPavement().getId());
        dto.setPavementName(entity.getPavement().getName());
        dto.setAlertType(entity.getAlertType());
        dto.setSeverity(entity.getSeverity());
        dto.setMessage(entity.getMessage());
        dto.setWaterDepthMm(entity.getWaterDepthMm());
        dto.setRecessionTimeSec(entity.getRecessionTimeSec());
        dto.setAcknowledged(entity.getAcknowledged());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
