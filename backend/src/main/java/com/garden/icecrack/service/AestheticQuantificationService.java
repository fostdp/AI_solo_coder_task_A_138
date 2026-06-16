package com.garden.icecrack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.garden.icecrack.dto.AestheticResultDTO;
import com.garden.icecrack.entity.AestheticResult;
import com.garden.icecrack.entity.Pavement;
import com.garden.icecrack.repository.AestheticResultRepository;
import com.garden.icecrack.repository.PavementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AestheticQuantificationService {

    private final AestheticResultRepository aestheticResultRepository;
    private final PavementRepository pavementRepository;
    private final ObjectMapper objectMapper;

    public AestheticResultDTO analyzePavement(UUID pavementId) {
        Pavement pavement = pavementRepository.findById(pavementId)
                .orElseThrow(() -> new RuntimeException("Pavement not found"));

        double areaLength = pavement.getAreaLength();
        double areaWidth = pavement.getAreaWidth();
        double area = areaLength * areaWidth;

        List<double[][]> segments;
        if (pavement.getCrackPattern() != null && !pavement.getCrackPattern().isBlank()) {
            segments = parseCrackPattern(pavement.getCrackPattern());
        } else {
            segments = generateCrackPattern(areaLength, areaWidth);
        }

        double fractalDimension = computeBoxCountingDimension(segments, areaLength, areaWidth);
        double infoEntropy = computeInformationEntropy(segments);
        double totalCrackLength = computeTotalCrackLength(segments);
        double crackDensity = totalCrackLength / area;
        double normalizedEntropy = infoEntropy / (Math.log(18) / Math.log(2));
        double visualComplexity = 0.4 * fractalDimension + 0.3 * normalizedEntropy + 0.3 * crackDensity;
        double patternSymmetry = computePatternSymmetry(segments, areaLength, areaWidth);

        String crackSegmentsJson;
        try {
            crackSegmentsJson = objectMapper.writeValueAsString(segments);
        } catch (Exception e) {
            crackSegmentsJson = "[]";
        }

        AestheticResult result = new AestheticResult();
        result.setPavement(pavement);
        result.setCalcTime(LocalDateTime.now());
        result.setFractalDimension(fractalDimension);
        result.setBoxCountingDim(fractalDimension);
        result.setInfoEntropy(infoEntropy);
        result.setVisualComplexity(visualComplexity);
        result.setCrackCount(segments.size());
        result.setAvgCrackLength(segments.isEmpty() ? 0.0 : totalCrackLength / segments.size());
        result.setCrackDensity(crackDensity);
        result.setPatternSymmetry(patternSymmetry);
        result.setCrackSegments(crackSegmentsJson);

        AestheticResult saved = aestheticResultRepository.save(result);
        return toDTO(saved);
    }

    public List<AestheticResultDTO> getAnalysisHistory(UUID pavementId) {
        return aestheticResultRepository.findByPavementIdOrderByCalcTimeDesc(pavementId)
                .stream().map(this::toDTO).toList();
    }

    private List<double[][]> parseCrackPattern(String json) {
        try {
            List<?> rawList = objectMapper.readValue(json, List.class);
            List<double[][]> segments = new ArrayList<>();
            for (Object item : rawList) {
                if (item instanceof List<?> coords && coords.size() == 4) {
                    double[][] seg = new double[2][2];
                    if (coords.get(0) instanceof List<?> p0 && coords.get(1) instanceof List<?> p1) {
                        seg[0][0] = ((Number) p0.get(0)).doubleValue();
                        seg[0][1] = ((Number) p0.get(1)).doubleValue();
                        seg[1][0] = ((Number) p1.get(0)).doubleValue();
                        seg[1][1] = ((Number) p1.get(1)).doubleValue();
                    }
                    segments.add(seg);
                }
            }
            return segments;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<double[][]> generateCrackPattern(double areaLength, double areaWidth) {
        List<double[][]> segments = new ArrayList<>();
        Random random = new Random(42);
        int numPoints = 20 + random.nextInt(10);
        double[][] points = new double[numPoints][2];
        for (int i = 0; i < numPoints; i++) {
            points[i][0] = random.nextDouble();
            points[i][1] = random.nextDouble();
        }

        for (int i = 0; i < numPoints; i++) {
            double minDist1 = Double.MAX_VALUE;
            double minDist2 = Double.MAX_VALUE;
            int nearest1 = -1;
            int nearest2 = -1;
            for (int j = 0; j < numPoints; j++) {
                if (i == j) continue;
                double dx = points[i][0] - points[j][0];
                double dy = points[i][1] - points[j][1];
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < minDist1) {
                    minDist2 = minDist1;
                    nearest2 = nearest1;
                    minDist1 = dist;
                    nearest1 = j;
                } else if (dist < minDist2) {
                    minDist2 = dist;
                    nearest2 = j;
                }
            }
            double irregularity = 0.1 * random.nextGaussian();
            double[][] seg1 = new double[2][2];
            seg1[0][0] = points[i][0] * areaLength;
            seg1[0][1] = points[i][1] * areaWidth;
            seg1[1][0] = (points[nearest1][0] + irregularity) * areaLength;
            seg1[1][1] = (points[nearest1][1] + irregularity) * areaWidth;
            segments.add(seg1);

            if (nearest2 >= 0 && random.nextDouble() < 0.5) {
                double irr2 = 0.1 * random.nextGaussian();
                double[][] seg2 = new double[2][2];
                seg2[0][0] = points[i][0] * areaLength;
                seg2[0][1] = points[i][1] * areaWidth;
                seg2[1][0] = (points[nearest2][0] + irr2) * areaLength;
                seg2[1][1] = (points[nearest2][1] + irr2) * areaWidth;
                segments.add(seg2);
            }
        }
        return segments;
    }

    private double computeBoxCountingDimension(List<double[][]> segments, double areaLength, double areaWidth) {
        int[] boxSizes = {2, 4, 8, 16, 32, 64};
        double[] logInverseS = new double[boxSizes.length];
        double[] logN = new double[boxSizes.length];

        for (int idx = 0; idx < boxSizes.length; idx++) {
            int s = boxSizes[idx];
            boolean[][] occupied = new boolean[s][s];
            for (double[][] seg : segments) {
                int bx0 = Math.min((int) (seg[0][0] / areaLength * s), s - 1);
                int by0 = Math.min((int) (seg[0][1] / areaWidth * s), s - 1);
                int bx1 = Math.min((int) (seg[1][0] / areaLength * s), s - 1);
                int by1 = Math.min((int) (seg[1][1] / areaWidth * s), s - 1);
                occupied[bx0][by0] = true;
                occupied[bx1][by1] = true;
                int minX = Math.min(bx0, bx1);
                int maxX = Math.max(bx0, bx1);
                int minY = Math.min(by0, by1);
                int maxY = Math.max(by0, by1);
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        occupied[x][y] = true;
                    }
                }
            }
            int count = 0;
            for (int i = 0; i < s; i++) {
                for (int j = 0; j < s; j++) {
                    if (occupied[i][j]) count++;
                }
            }
            logInverseS[idx] = Math.log(1.0 / s);
            logN[idx] = Math.log(Math.max(count, 1));
        }

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int n = boxSizes.length;
        for (int i = 0; i < n; i++) {
            sumX += logInverseS[i];
            sumY += logN[i];
            sumXY += logInverseS[i] * logN[i];
            sumX2 += logInverseS[i] * logInverseS[i];
        }
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        return slope;
    }

    private double computeInformationEntropy(List<double[][]> segments) {
        int numBins = 18;
        int[] bins = new int[numBins];
        int total = 0;

        for (double[][] seg : segments) {
            double dx = seg[1][0] - seg[0][0];
            double dy = seg[1][1] - seg[0][1];
            double angle = Math.toDegrees(Math.atan2(dy, dx));
            if (angle < 0) angle += 180;
            if (angle >= 180) angle = 179.999;
            int bin = (int) (angle / 10.0);
            if (bin >= numBins) bin = numBins - 1;
            bins[bin]++;
            total++;
        }

        if (total == 0) return 0.0;

        double entropy = 0.0;
        for (int count : bins) {
            if (count > 0) {
                double p = (double) count / total;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }
        return entropy;
    }

    private double computeTotalCrackLength(List<double[][]> segments) {
        double total = 0.0;
        for (double[][] seg : segments) {
            double dx = seg[1][0] - seg[0][0];
            double dy = seg[1][1] - seg[0][1];
            total += Math.sqrt(dx * dx + dy * dy);
        }
        return total;
    }

    private double computePatternSymmetry(List<double[][]> segments, double areaLength, double areaWidth) {
        int[] quadrantCount = new int[4];
        for (double[][] seg : segments) {
            double midX = (seg[0][0] + seg[1][0]) / 2.0;
            double midY = (seg[0][1] + seg[1][1]) / 2.0;
            boolean right = midX >= areaLength / 2.0;
            boolean top = midY >= areaWidth / 2.0;
            int q = (top ? 2 : 0) + (right ? 1 : 0);
            quadrantCount[q]++;
        }
        int total = segments.size();
        if (total == 0) return 1.0;

        double expected = total / 4.0;
        double chiSquare = 0.0;
        for (int count : quadrantCount) {
            double diff = count - expected;
            chiSquare += (diff * diff) / expected;
        }
        double maxChi = 3.0 * expected;
        double symmetry = 1.0 - Math.min(chiSquare / maxChi, 1.0);
        return symmetry;
    }

    private AestheticResultDTO toDTO(AestheticResult entity) {
        AestheticResultDTO dto = new AestheticResultDTO();
        dto.setId(entity.getId());
        dto.setPavementId(entity.getPavement().getId());
        dto.setFractalDimension(entity.getFractalDimension());
        dto.setBoxCountingDim(entity.getBoxCountingDim());
        dto.setInfoEntropy(entity.getInfoEntropy());
        dto.setVisualComplexity(entity.getVisualComplexity());
        dto.setCrackCount(entity.getCrackCount());
        dto.setAvgCrackLength(entity.getAvgCrackLength());
        dto.setCrackDensity(entity.getCrackDensity());
        dto.setPatternSymmetry(entity.getPatternSymmetry());
        dto.setCrackSegments(entity.getCrackSegments());
        return dto;
    }
}
