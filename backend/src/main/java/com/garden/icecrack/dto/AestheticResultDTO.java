package com.garden.icecrack.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class AestheticResultDTO {
    private Long id;
    private UUID pavementId;
    private Double fractalDimension;
    private Double boxCountingDim;
    private Double infoEntropy;
    private Double visualComplexity;
    private Integer crackCount;
    private Double avgCrackLength;
    private Double crackDensity;
    private Double patternSymmetry;
    private String crackSegments;
}
