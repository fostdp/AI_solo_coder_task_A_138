package com.garden.icecrack.drainage_simulator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "drainage")
public class DrainageProperties {

    private double gravity = 9.81;
    private double frictionCoeff = 0.03;
    private double timeStep = 0.5;
    private int sampleInterval = 10;
    private double recessionThresholdM = 0.001;
    private double recessionAlertThresholdSec = 1800;
    private double crackInfiltrationWidthFactor = 10.0;
    private double crackInfiltrationStepFactor = 0.1;
    private double crackFluxBaseMultiplier = 0.8;
    private double crackDepthEnhanceDivisor = 5.0;
    private double slopeVerticalReduction = 0.5;
    private double cflFactor = 0.5;
}
