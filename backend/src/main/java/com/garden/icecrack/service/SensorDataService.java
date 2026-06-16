package com.garden.icecrack.service;

import com.garden.icecrack.dto.SensorDataDTO;
import com.garden.icecrack.entity.Pavement;
import com.garden.icecrack.entity.SensorData;
import com.garden.icecrack.repository.PavementRepository;
import com.garden.icecrack.repository.SensorDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SensorDataService {

    private final SensorDataRepository sensorDataRepository;
    private final PavementRepository pavementRepository;

    public List<SensorDataDTO> getLatestData(UUID pavementId, int limit) {
        List<SensorData> allData = sensorDataRepository.findByPavementIdOrderByRecordedAtDesc(pavementId);
        return allData.stream().limit(limit).map(this::toDTO).toList();
    }

    public SensorDataDTO addSensorData(SensorDataDTO dto) {
        Pavement pavement = pavementRepository.findById(dto.getPavementId())
                .orElseThrow(() -> new RuntimeException("Pavement not found"));
        SensorData entity = new SensorData();
        entity.setPavement(pavement);
        entity.setRecordedAt(dto.getRecordedAt() != null ? dto.getRecordedAt() : LocalDateTime.now());
        entity.setRainfallMm(dto.getRainfallMm());
        entity.setWaterDepthMm(dto.getWaterDepthMm());
        entity.setCrackWidthMm(dto.getCrackWidthMm());
        entity.setStepFrequency(dto.getStepFrequency());
        entity.setTemperature(dto.getTemperature());
        entity.setHumidity(dto.getHumidity());
        SensorData saved = sensorDataRepository.save(entity);
        return toDTO(saved);
    }

    public List<SensorDataDTO> getDataInRange(UUID pavementId, LocalDateTime start, LocalDateTime end) {
        List<SensorData> data = sensorDataRepository.findByPavementIdAndRecordedAtBetween(pavementId, start, end);
        return data.stream().map(this::toDTO).toList();
    }

    private SensorDataDTO toDTO(SensorData entity) {
        SensorDataDTO dto = new SensorDataDTO();
        dto.setId(entity.getId());
        dto.setPavementId(entity.getPavement().getId());
        dto.setRecordedAt(entity.getRecordedAt());
        dto.setRainfallMm(entity.getRainfallMm());
        dto.setWaterDepthMm(entity.getWaterDepthMm());
        dto.setCrackWidthMm(entity.getCrackWidthMm());
        dto.setStepFrequency(entity.getStepFrequency());
        dto.setTemperature(entity.getTemperature());
        dto.setHumidity(entity.getHumidity());
        return dto;
    }
}
