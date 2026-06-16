package com.garden.icecrack.alarm_ws.listener;

import com.garden.icecrack.alarm_ws.service.AlertService;
import com.garden.icecrack.common.event.SimulationCompletedEvent;
import com.garden.icecrack.drainage_simulator.config.DrainageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DrainageAlertListener {

    private final AlertService alertService;
    private final DrainageProperties drainageProperties;

    @EventListener
    public void onSimulationCompleted(SimulationCompletedEvent event) {
        if (event.getRecessionTimeSec() > drainageProperties.getRecessionAlertThresholdSec()) {
            String severity = event.getRecessionTimeSec() > drainageProperties.getRecessionAlertThresholdSec() * 2
                    ? "HIGH" : "WARNING";
            alertService.createAlert(
                    event.getPavementId(),
                    "DRAINAGE",
                    severity,
                    "Recession time exceeded threshold: " + (long) event.getRecessionTimeSec() + "s",
                    event.getPeakWaterDepthMm(),
                    event.getRecessionTimeSec()
            );
        }
    }
}
