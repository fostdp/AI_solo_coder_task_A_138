package com.garden.icecrack.dtu_receiver.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class SensorDataDTO {
    private Long id;
    private UUID pavementId;
    private LocalDateTime recordedAt;
    private Double rainfallMm;
    private Double waterDepthMm;
    private Double crackWidthMm;
    private Double stepFrequency;
    private Double temperature;
    private Double humidity;
}
