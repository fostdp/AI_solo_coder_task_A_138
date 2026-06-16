package com.garden.icecrack.common.event;

import java.util.UUID;

public class SimulationCompletedEvent {

    private final UUID pavementId;
    private final double recessionTimeSec;
    private final double peakWaterDepthMm;
    private final double drainageRate;
    private final double infiltrationRate;
    private final double surfaceRunoffRate;

    public SimulationCompletedEvent(UUID pavementId, double recessionTimeSec, double peakWaterDepthMm,
                                    double drainageRate, double infiltrationRate, double surfaceRunoffRate) {
        this.pavementId = pavementId;
        this.recessionTimeSec = recessionTimeSec;
        this.peakWaterDepthMm = peakWaterDepthMm;
        this.drainageRate = drainageRate;
        this.infiltrationRate = infiltrationRate;
        this.surfaceRunoffRate = surfaceRunoffRate;
    }

    public UUID getPavementId() { return pavementId; }
    public double getRecessionTimeSec() { return recessionTimeSec; }
    public double getPeakWaterDepthMm() { return peakWaterDepthMm; }
    public double getDrainageRate() { return drainageRate; }
    public double getInfiltrationRate() { return infiltrationRate; }
    public double getSurfaceRunoffRate() { return surfaceRunoffRate; }
}
