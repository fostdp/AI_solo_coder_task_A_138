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
import java.util.Random;
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
        double dx = areaLength / gridRes;
        double dy = areaWidth / gridRes;

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
        long seed = request.getPavementId().getMostSignificantBits() ^ request.getPavementId().getLeastSignificantBits();
        boolean[][] crackMaskX = generateCrackMask(gridRes, crackWidthMm, seed, 0);
        boolean[][] crackMaskY = generateCrackMask(gridRes, crackWidthMm, seed, 1);
        double[] crackFluxFactorX = computeCrackFluxFactor(crackMaskX, crackWidthMm);
        double[] crackFluxFactorY = computeCrackFluxFactor(crackMaskY, crackWidthMm);

        double peakWaterDepth = initialDepthM;
        double recessionTimeSec = duration;
        boolean recessionRecorded = false;

        List<Map<String, Object>> timeSeriesList = new ArrayList<>();

        double totalFluxX = 0.0;
        double totalFluxY = 0.0;
        int fluxCount = 0;

        int totalSteps = (int) (duration / dt);
        for (int step = 0; step < totalSteps; step++) {
            double time = step * dt;

            double[][] fluxX = new double[gridRes + 1][gridRes];
            double[][] fluxY = new double[gridRes][gridRes + 1];

            for (int i = 0; i <= gridRes; i++) {
                for (int j = 0; j < gridRes; j++) {
                    double hL = (i == 0) ? grid[0][j] : grid[i - 1][j];
                    double hR = (i == gridRes) ? grid[gridRes - 1][j] : grid[i][j];
                    double uL = (g * hL * slopeRad) / (frictionCoeff + 1e-9);
                    double uR = (g * hR * slopeRad) / (frictionCoeff + 1e-9);
                    double FL = hL * uL;
                    double FR = hR * uR;
                    double waveSpeed = Math.max(Math.abs(uL) + Math.sqrt(g * Math.max(hL, 1e-9)),
                            Math.abs(uR) + Math.sqrt(g * Math.max(hR, 1e-9)));
                    double alpha = 0.5 * waveSpeed;
                    double lfFlux = 0.5 * (FL + FR) - alpha * (hR - hL);
                    double crackFactor = (i < gridRes) ? crackFluxFactorX[i] : 1.0;
                    fluxX[i][j] = lfFlux * crackFactor;
                    totalFluxX += Math.abs(fluxX[i][j]);
                    fluxCount++;
                }
            }

            for (int i = 0; i < gridRes; i++) {
                for (int j = 0; j <= gridRes; j++) {
                    double hD = (j == 0) ? grid[i][0] : grid[i][j - 1];
                    double hU = (j == gridRes) ? grid[i][gridRes - 1] : grid[i][j];
                    double vD = (g * hD * slopeRad * 0.5) / (frictionCoeff + 1e-9);
                    double vU = (g * hU * slopeRad * 0.5) / (frictionCoeff + 1e-9);
                    double FD = hD * vD;
                    double FU = hU * vU;
                    double waveSpeed = Math.max(Math.abs(vD) + Math.sqrt(g * Math.max(hD, 1e-9)),
                            Math.abs(vU) + Math.sqrt(g * Math.max(hU, 1e-9)));
                    double alpha = 0.5 * waveSpeed;
                    double lfFlux = 0.5 * (FD + FU) - alpha * (hU - hD);
                    double crackFactor = (j < gridRes) ? crackFluxFactorY[j] : 1.0;
                    fluxY[i][j] = lfFlux * crackFactor;
                    totalFluxY += Math.abs(fluxY[i][j]);
                }
            }

            double stepInfiltrationBase = basePermeability * (1 + crackWidthMm / 10.0) * (1 + 0.1 * stepFrequency) * dt;
            for (int i = 0; i < gridRes; i++) {
                for (int j = 0; j < gridRes; j++) {
                    double netFluxX = (fluxX[i][j] - fluxX[i + 1][j]) / dx;
                    double netFluxY = (fluxY[i][j] - fluxY[i][j + 1]) / dy;
                    double netFlux = netFluxX + netFluxY;
                    boolean hasCrack = (i > 0 && crackMaskX[i][j])
                            || (j > 0 && crackMaskY[i][j]);
                    double infiltration = stepInfiltrationBase * (hasCrack ? (1.0 + crackWidthMm / 5.0) : 1.0);
                    double rainInput = rainfallRate * dt / 1000.0;
                    double newDepth = grid[i][j] + dt * netFlux + rainInput - infiltration;
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

        double avgFlux = fluxCount > 0 ? (totalFluxX + totalFluxY) / (2.0 * fluxCount) : 0.0;
        double drainageRate = avgFlux + (g * initialDepthM * slopeRad) / frictionCoeff;
        double infiltrationRate = basePermeability * (1 + crackWidthMm / 10.0) * (1 + 0.1 * stepFrequency);
        double surfaceRunoffRate = avgFlux;

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

    private boolean[][] generateCrackMask(int gridRes, double crackWidthMm, long seed, int axis) {
        boolean[][] mask = new boolean[gridRes + 1][gridRes];
        if (crackWidthMm < 0.1) return mask;
        Random rng = new Random(seed + axis * 1337);
        int numCracks = (int) Math.max(2, gridRes * crackWidthMm / 20.0);
        for (int c = 0; c < numCracks; c++) {
            int pos = rng.nextInt(gridRes + 1);
            int centerRow = rng.nextInt(gridRes);
            int length = (int) (gridRes * (0.2 + rng.nextDouble() * 0.6));
            int current = pos;
            for (int k = 0; k < length; k++) {
                int row = centerRow + (k - length / 2);
                if (row >= 0 && row < gridRes && current >= 0 && current < gridRes + 1) {
                    mask[current][row] = true;
                }
                current += rng.nextDouble() < 0.4 ? (rng.nextBoolean() ? 1 : -1) : 0;
            }
        }
        return mask;
    }

    private double[] computeCrackFluxFactor(boolean[][] crackMask, double crackWidthMm) {
        int gridRes = crackMask.length - 1;
        double[] factors = new double[gridRes + 1];
        double baseFactor = 1.0 + crackWidthMm * 0.8;
        for (int i = 0; i < gridRes + 1; i++) {
            int count = 0;
            for (boolean[] row : crackMask[i]) {
                if (row) count++;
            }
            double density = (double) count / crackMask[i].length;
            factors[i] = 1.0 + density * (baseFactor - 1.0);
        }
        return factors;
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
