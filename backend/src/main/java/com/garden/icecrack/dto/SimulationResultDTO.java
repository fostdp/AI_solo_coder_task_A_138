package com.garden.icecrack.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class SimulationResultDTO {
    private Long id;
    private UUID pavementId;
    private Double initialWaterDepth;
    private Double recessionTimeSec;
    private Double peakWaterDepth;
    private Double drainageRate;
    private Double infiltrationRate;
    private Double surfaceRunoffRate;
    private String timeSeries;
    private String gridData;
    private Boolean alertTriggered;
    private String alertMessage;
}
