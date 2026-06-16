package com.garden.icecrack.drainage_simulator.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class SimulationRequestDTO {
    private UUID pavementId;
    private Double rainfallMm;
    private Double initialWaterDepthMm;
    private Double crackWidthMm;
    private Double stepFrequency;
    private Double simulationDurationSec = 3600.0;
    private Integer gridResolution = 20;
}
