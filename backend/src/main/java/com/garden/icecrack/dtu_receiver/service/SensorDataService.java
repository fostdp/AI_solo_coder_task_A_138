package com.garden.icecrack.dtu_receiver.service;

import com.garden.icecrack.common.entity.Pavement;
import com.garden.icecrack.common.event.SensorDataReceivedEvent;
import com.garden.icecrack.common.repository.PavementRepository;
import com.garden.icecrack.dtu_receiver.dto.SensorDataDTO;
import com.garden.icecrack.dtu_receiver.entity.SensorData;
import com.garden.icecrack.dtu_receiver.repository.SensorDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SensorDataService {

    private final SensorDataRepository sensorDataRepository;
    private final PavementRepository pavementRepository;
    private final ApplicationEventPublisher eventPublisher;

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

        eventPublisher.publishEvent(new SensorDataReceivedEvent(
                dto.getPavementId(),
                dto.getRainfallMm() != null ? dto.getRainfallMm() : 0,
                dto.getWaterDepthMm() != null ? dto.getWaterDepthMm() : 0,
                dto.getCrackWidthMm() != null ? dto.getCrackWidthMm() : 0,
                dto.getStepFrequency() != null ? dto.getStepFrequency() : 0
        ));

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
