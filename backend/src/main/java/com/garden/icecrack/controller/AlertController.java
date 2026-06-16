package com.garden.icecrack.controller;

import com.garden.icecrack.dto.AlertDTO;
import com.garden.icecrack.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping("/unacknowledged")
    public ResponseEntity<List<AlertDTO>> unacknowledged() {
        return ResponseEntity.ok(alertService.getUnacknowledgedAlerts());
    }

    @GetMapping("/pavement/{pavementId}")
    public ResponseEntity<List<AlertDTO>> byPavement(@PathVariable UUID pavementId) {
        return ResponseEntity.ok(alertService.getAlertsByPavement(pavementId));
    }

    @PutMapping("/{alertId}/acknowledge")
    public ResponseEntity<AlertDTO> acknowledge(@PathVariable Long alertId) {
        return ResponseEntity.ok(alertService.acknowledgeAlert(alertId));
    }
}
