package com.garden.icecrack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.garden.icecrack.dto.SimulationRequestDTO;
import com.garden.icecrack.dto.SimulationResultDTO;
import com.garden.icecrack.entity.Pavement;
import com.garden.icecrack.entity.SimulationResult;
import com.garden.icecrack.repository.PavementRepository;
import com.garden.icecrack.repository.SimulationResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DrainageSimulationService {

    private final SimulationResultRepository simulationResultRepository;
    private final PavementRepository pavementRepository;
    private final AlertService alertService;
    private final ObjectMapper objectMapper;

    public SimulationResultDTO runSimulation(SimulationRequestDTO request) {
        Pavement pavement = pavementRepository.findById(request.getPavementId())
                .orElseThrow(() -> new RuntimeException("Pavement not found"));

        double areaLength = pavement.getAreaLength();
        double areaWidth = pavement.getAreaWidth();
        double slopeAngle = pavement.getSlopeAngle() != null ? pavement.getSlopeAngle() : 0.0;
        double basePermeability = pavement.getBasePermeability() != null ? pavement.getBasePermeability() : 0.001;

        int gridRes = request.getGridResolution() != null ? request.getGridResolution() : 20;
        double dt = 0.5;
        double duration = request.getSimulationDurationSec() != null ? request.getSimulationDurationSec() : 3600.0;
        double g = 9.81;
        double frictionCoeff = 0.03;
        double slopeRad = slopeToRadians(slopeAngle);

        double[][] grid = new double[gridRes][gridRes];
        double initialDepthM = (request.getInitialWaterDepthMm() != null ? request.getInitialWaterDepthMm() : 0.0) / 1000.0;
        for (int i = 0; i < gridRes; i++) {
            for (int j = 0; j < gridRes; j++) {
                grid[i][j] = initialDepthM;
            }
        }

        double rainfallRate = request.getRainfallMm() != null ? request.getRainfallMm() : 0.0;
        double crackWidthMm = request.getCrackWidthMm() != null ? request.getCrackWidthMm() : 0.0;
        double stepFrequency = request.getStepFrequency() != null ? request.getStepFrequency() : 0.0;

        double peakWaterDepth = initialDepthM;
        double recessionTimeSec = duration;
        boolean recessionRecorded = false;

        List<Map<String, Object>> timeSeriesList = new ArrayList<>();

        int totalSteps = (int) (duration / dt);
        for (int step = 0; step < totalSteps; step++) {
            double time = step * dt;

            for (int i = 0; i < gridRes; i++) {
                for (int j = 0; j < gridRes; j++) {
                    double h = grid[i][j];
                    double surfaceFlow = (g * h * slopeRad) / frictionCoeff;
                    double outflow = surfaceFlow * dt;
                    double infiltration = basePermeability * (1 + crackWidthMm / 10.0) * (1 + 0.1 * stepFrequency) * dt;
                    double newDepth = h + (rainfallRate * dt / 1000.0) - outflow - infiltration;
                    grid[i][j] = Math.max(0.0, newDepth);
                }
            }

            double maxDepth = 0.0;
            double totalDepth = 0.0;
            for (int i = 0; i < gridRes; i++) {
                for (int j = 0; j < gridRes; j++) {
                    totalDepth += grid[i][j];
                    if (grid[i][j] > maxDepth) {
                        maxDepth = grid[i][j];
                    }
                }
            }
            double avgDepth = totalDepth / (gridRes * gridRes);

            if (maxDepth > peakWaterDepth) {
                peakWaterDepth = maxDepth;
            }

            if (step % 10 == 0) {
                Map<String, Object> point = new HashMap<>();
                point.put("time", time);
                point.put("avgDepth", avgDepth);
                point.put("maxDepth", maxDepth);
                timeSeriesList.add(point);
            }

            if (!recessionRecorded && maxDepth < 0.001) {
                recessionTimeSec = time;
                recessionRecorded = true;
                break;
            }
        }

        String timeSeriesJson;
        String gridDataJson;
        try {
            timeSeriesJson = objectMapper.writeValueAsString(timeSeriesList);
            gridDataJson = objectMapper.writeValueAsString(grid);
        } catch (Exception e) {
            timeSeriesJson = "[]";
            gridDataJson = "[]";
        }

        boolean alertTriggered = recessionTimeSec > 1800;
        String alertMessage = null;
        if (alertTriggered) {
            alertService.createAlert(
                    request.getPavementId(),
                    "DRAINAGE",
                    "HIGH",
                    "Recession time exceeded 30 minutes: " + recessionTimeSec + "s",
                    peakWaterDepth * 1000.0,
                    recessionTimeSec
            );
            alertMessage = "Recession time exceeded 30 minutes";
        }

        double totalOutflow = 0.0;
        double totalInfiltration = 0.0;
        for (int i = 0; i < gridRes; i++) {
            for (int j = 0; j < gridRes; j++) {
                totalOutflow += (g * initialDepthM * slopeRad) / frictionCoeff;
                totalInfiltration += basePermeability * (1 + crackWidthMm / 10.0) * (1 + 0.1 * stepFrequency);
            }
        }
        double drainageRate = totalOutflow / (gridRes * gridRes);
        double infiltrationRate = totalInfiltration / (gridRes * gridRes);
        double surfaceRunoffRate = drainageRate;

        SimulationResult result = new SimulationResult();
        result.setPavement(pavement);
        result.setSimTime(LocalDateTime.now());
        result.setInitialWaterDepth(initialDepthM);
        result.setRecessionTimeSec(recessionTimeSec);
        result.setPeakWaterDepth(peakWaterDepth);
        result.setDrainageRate(drainageRate);
        result.setInfiltrationRate(infiltrationRate);
        result.setSurfaceRunoffRate(surfaceRunoffRate);
        result.setTimeSeries(timeSeriesJson);
        result.setGridData(gridDataJson);
        result.setAlertTriggered(alertTriggered);
        result.setAlertMessage(alertMessage);

        SimulationResult saved = simulationResultRepository.save(result);
        return toDTO(saved);
    }

    public List<SimulationResultDTO> getSimulationHistory(UUID pavementId) {
        return simulationResultRepository.findByPavementIdOrderBySimTimeDesc(pavementId)
                .stream().map(this::toDTO).toList();
    }

    private double slopeToRadians(double degrees) {
        return Math.toRadians(degrees);
    }

    private SimulationResultDTO toDTO(SimulationResult entity) {
        SimulationResultDTO dto = new SimulationResultDTO();
        dto.setId(entity.getId());
        dto.setPavementId(entity.getPavement().getId());
        dto.setInitialWaterDepth(entity.getInitialWaterDepth());
        dto.setRecessionTimeSec(entity.getRecessionTimeSec());
        dto.setPeakWaterDepth(entity.getPeakWaterDepth());
        dto.setDrainageRate(entity.getDrainageRate());
        dto.setInfiltrationRate(entity.getInfiltrationRate());
        dto.setSurfaceRunoffRate(entity.getSurfaceRunoffRate());
        dto.setTimeSeries(entity.getTimeSeries());
        dto.setGridData(entity.getGridData());
        dto.setAlertTriggered(entity.getAlertTriggered());
        dto.setAlertMessage(entity.getAlertMessage());
        return dto;
    }
}
