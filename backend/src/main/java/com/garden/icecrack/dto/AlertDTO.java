package com.garden.icecrack.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AlertDTO {
    private Long id;
    private UUID pavementId;
    private String pavementName;
    private String alertType;
    private String severity;
    private String message;
    private Double waterDepthMm;
    private Double recessionTimeSec;
    private Boolean acknowledged;
    private LocalDateTime createdAt;
}
