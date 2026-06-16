package com.garden.icecrack.dtu_receiver.controller;

import com.garden.icecrack.dtu_receiver.dto.SensorDataDTO;
import com.garden.icecrack.dtu_receiver.service.SensorDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sensor-data")
@RequiredArgsConstructor
public class SensorDataController {

    private final SensorDataService sensorDataService;

    @GetMapping("/{pavementId}/latest")
    public ResponseEntity<List<SensorDataDTO>> getLatest(
            @PathVariable UUID pavementId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(sensorDataService.getLatestData(pavementId, limit));
    }

    @GetMapping("/{pavementId}/range")
    public ResponseEntity<List<SensorDataDTO>> getRange(
            @PathVariable UUID pavementId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime start,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime end) {
        return ResponseEntity.ok(sensorDataService.getDataInRange(pavementId, start, end));
    }

    @PostMapping
    public ResponseEntity<SensorDataDTO> add(@RequestBody SensorDataDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sensorDataService.addSensorData(dto));
    }
}
