package com.garden.icecrack.common.event;

import java.util.UUID;

public class SensorDataReceivedEvent {

    private final UUID pavementId;
    private final double rainfallMm;
    private final double waterDepthMm;
    private final double crackWidthMm;
    private final double stepFrequency;

    public SensorDataReceivedEvent(UUID pavementId, double rainfallMm, double waterDepthMm,
                                   double crackWidthMm, double stepFrequency) {
        this.pavementId = pavementId;
        this.rainfallMm = rainfallMm;
        this.waterDepthMm = waterDepthMm;
        this.crackWidthMm = crackWidthMm;
        this.stepFrequency = stepFrequency;
    }

    public UUID getPavementId() { return pavementId; }
    public double getRainfallMm() { return rainfallMm; }
    public double getWaterDepthMm() { return waterDepthMm; }
    public double getCrackWidthMm() { return crackWidthMm; }
    public double getStepFrequency() { return stepFrequency; }
}
