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

        List<double[][]> extendedSegments = periodicallyExtendSegments(segments, areaLength, areaWidth);

        double fractalDimension = computeUnbiasedBoxCountingDimension(extendedSegments, areaLength, areaWidth);
        double rawBoxCountingDim = computeRawBoxCountingDimension(segments, areaLength, areaWidth);
        double infoEntropy = computeInformationEntropy(segments);
        double totalCrackLength = computeTotalCrackLength(segments);
        double crackDensity = totalCrackLength / area;
        double normalizedEntropy = infoEntropy / (Math.log(18) / Math.log(2));
        double visualComplexity = 0.4 * fractalDimension + 0.3 * normalizedEntropy + 0.3 * Math.min(crackDensity * 10, 1.0);
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
        result.setBoxCountingDim(rawBoxCountingDim);
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
            Object raw = objectMapper.readValue(json, Object.class);
            if (raw instanceof java.util.Map<?, ?> map) {
                Number seed = (Number) map.getOrDefault("seed", 42);
                Number seg = (Number) map.getOrDefault("segments", 35);
                Number irr = (Number) map.getOrDefault("irregularity", 0.7);
                return generateCrackPatternWithParams(10.0, 10.0, seed.longValue(), seg.intValue(), irr.doubleValue());
            } else if (raw instanceof List<?> rawList) {
                List<double[][]> segments = new ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof List<?> coords) {
                        double[][] seg = new double[2][2];
                        if (coords.get(0) instanceof List<?> p0 && coords.get(1) instanceof List<?> p1) {
                            seg[0][0] = ((Number) p0.get(0)).doubleValue();
                            seg[0][1] = ((Number) p0.get(1)).doubleValue();
                            seg[1][0] = ((Number) p1.get(0)).doubleValue();
                            seg[1][1] = ((Number) p1.get(1)).doubleValue();
                            segments.add(seg);
                        }
                    }
                }
                return segments;
            }
            return new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<double[][]> generateCrackPattern(double areaLength, double areaWidth) {
        return generateCrackPatternWithParams(areaLength, areaWidth, 42L, 35, 0.7);
    }

    private List<double[][]> generateCrackPatternWithParams(double areaLength, double areaWidth,
                                                            long seed, int targetSegments, double irregularity) {
        List<double[][]> segments = new ArrayList<>();
        Random random = new Random(seed);
        int numPoints = 15 + random.nextInt(10);
        double[][] points = new double[numPoints][2];
        for (int i = 0; i < numPoints; i++) {
            points[i][0] = random.nextDouble();
            points[i][1] = random.nextDouble();
        }

        for (int i = 0; i < numPoints && segments.size() < targetSegments; i++) {
            double minDist1 = Double.MAX_VALUE;
            double minDist2 = Double.MAX_VALUE;
            double minDist3 = Double.MAX_VALUE;
            int nearest1 = -1, nearest2 = -1, nearest3 = -1;
            for (int j = 0; j < numPoints; j++) {
                if (i == j) continue;
                double dx = points[i][0] - points[j][0];
                double dy = points[i][1] - points[j][1];
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < minDist1) {
                    minDist3 = minDist2; nearest3 = nearest2;
                    minDist2 = minDist1; nearest2 = nearest1;
                    minDist1 = dist; nearest1 = j;
                } else if (dist < minDist2) {
                    minDist3 = minDist2; nearest3 = nearest2;
                    minDist2 = dist; nearest2 = j;
                } else if (dist < minDist3) {
                    minDist3 = dist; nearest3 = j;
                }
            }
            double irr = irregularity * 0.1;
            addSegment(segments, points[i], points[nearest1], irr, random, areaLength, areaWidth);
            if (nearest2 >= 0 && random.nextDouble() < 0.7 && segments.size() < targetSegments) {
                addSegment(segments, points[i], points[nearest2], irr, random, areaLength, areaWidth);
            }
            if (nearest3 >= 0 && random.nextDouble() < 0.4 && segments.size() < targetSegments) {
                addSegment(segments, points[i], points[nearest3], irr, random, areaLength, areaWidth);
            }
        }
        return segments;
    }

    private void addSegment(List<double[][]> segments, double[] pA, double[] pB,
                            double irr, Random random, double L, double W) {
        double x1 = (pA[0] + irr * random.nextGaussian()) * L;
        double y1 = (pA[1] + irr * random.nextGaussian()) * W;
        double x2 = (pB[0] + irr * random.nextGaussian()) * L;
        double y2 = (pB[1] + irr * random.nextGaussian()) * W;
        if (random.nextDouble() < 0.35) {
            double tx = (pA[0] + pB[0]) / 2 + irr * random.nextGaussian();
            double ty = (pA[1] + pB[1]) / 2 + irr * random.nextGaussian();
            double mx = tx * L, my = ty * W;
            double[][] s1 = {{x1, y1}, {mx, my}};
            double[][] s2 = {{mx, my}, {x2, y2}};
            segments.add(s1);
            segments.add(s2);
        } else {
            double[][] seg = {{x1, y1}, {x2, y2}};
            segments.add(seg);
        }
    }

    private List<double[][]> periodicallyExtendSegments(List<double[][]> segments, double L, double W) {
        List<double[][]> extended = new ArrayList<>(segments.size() * 4);
        for (int ix = -1; ix <= 1; ix++) {
            for (int iy = -1; iy <= 1; iy++) {
                if (ix == 0 && iy == 0) {
                    extended.addAll(segments);
                } else {
                    double ox = ix * L;
                    double oy = iy * W;
                    for (double[][] seg : segments) {
                        double[][] es = {
                            {seg[0][0] + ox, seg[0][1] + oy},
                            {seg[1][0] + ox, seg[1][1] + oy}
                        };
                        extended.add(es);
                    }
                }
            }
        }
        return extended;
    }

    private double computeUnbiasedBoxCountingDimension(List<double[][]> segments, double L, double W) {
        int[] boxSizes = {2, 4, 8, 16, 32, 64, 128};
        int n = boxSizes.length;
        double[] logInvS = new double[n];
        double[] logN = new double[n];
        double[] weights = new double[n];
        double padL = L * 3.0;
        double padW = W * 3.0;
        double offL = -L;
        double offW = -W;

        for (int idx = 0; idx < n; idx++) {
            int s = boxSizes[idx];
            boolean[][] occupied = new boolean[s][s];
            for (double[][] seg : segments) {
                double x0 = (seg[0][0] - offL) / padL * s;
                double y0 = (seg[0][1] - offW) / padW * s;
                double x1 = (seg[1][0] - offL) / padL * s;
                double y1 = (seg[1][1] - offW) / padW * s;
                ddaLine(x0, y0, x1, y1, occupied, s);
            }
            int boundaryBand = Math.max(1, s / 6);
            int interiorCount = 0;
            int totalCount = 0;
            for (int i = 0; i < s; i++) {
                for (int j = 0; j < s; j++) {
                    if (occupied[i][j]) {
                        totalCount++;
                        if (i >= boundaryBand && i < s - boundaryBand
                                && j >= boundaryBand && j < s - boundaryBand) {
                            interiorCount++;
                        }
                    }
                }
            }
            int interiorArea = (s - 2 * boundaryBand) * (s - 2 * boundaryBand);
            int totalArea = s * s;
            double interiorDensity = (double) interiorCount / Math.max(1, interiorArea);
            double correctedCount = totalCount + (totalArea - (s * s - interiorArea))
                    * Math.max(interiorDensity, 1.0 / totalArea);
            if (totalCount == 0) correctedCount = 1;
            double brownCorrection = (double) (s * s) / Math.max(1, (s - 2) * (s - 2));
            double finalCount = Math.max(totalCount, correctedCount * 0.3 + totalCount * 0.7) * Math.pow(brownCorrection, 0.15);
            logInvS[idx] = Math.log(1.0 / s);
            logN[idx] = Math.log(finalCount);
            weights[idx] = Math.log(s + 1);
        }
        return weightedLinearRegressionSlope(logInvS, logN, weights);
    }

    private double computeRawBoxCountingDimension(List<double[][]> segments, double L, double W) {
        int[] boxSizes = {2, 4, 8, 16, 32, 64};
        int n = boxSizes.length;
        double[] logInvS = new double[n];
        double[] logN = new double[n];
        for (int idx = 0; idx < n; idx++) {
            int s = boxSizes[idx];
            boolean[][] occupied = new boolean[s][s];
            for (double[][] seg : segments) {
                double x0 = seg[0][0] / L * s;
                double y0 = seg[0][1] / W * s;
                double x1 = seg[1][0] / L * s;
                double y1 = seg[1][1] / W * s;
                ddaLine(x0, y0, x1, y1, occupied, s);
            }
            int count = 0;
            for (int i = 0; i < s; i++) {
                for (int j = 0; j < s; j++) {
                    if (occupied[i][j]) count++;
                }
            }
            logInvS[idx] = Math.log(1.0 / s);
            logN[idx] = Math.log(Math.max(count, 1));
        }
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += logInvS[i];
            sumY += logN[i];
            sumXY += logInvS[i] * logN[i];
            sumX2 += logInvS[i] * logInvS[i];
        }
        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
    }

    private void ddaLine(double x0, double y0, double x1, double y1, boolean[][] occupied, int s) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)));
        if (steps == 0) {
            int bx = Math.min(Math.max((int) Math.floor(x0), 0), s - 1);
            int by = Math.min(Math.max((int) Math.floor(y0), 0), s - 1);
            occupied[bx][by] = true;
            return;
        }
        double incX = dx / steps;
        double incY = dy / steps;
        double cx = x0;
        double cy = y0;
        int lastBx = -1, lastBy = -1;
        for (int k = 0; k <= steps; k++) {
            int bx = Math.min(Math.max((int) Math.floor(cx), 0), s - 1);
            int by = Math.min(Math.max((int) Math.floor(cy), 0), s - 1);
            if (bx != lastBx || by != lastBy) {
                occupied[bx][by] = true;
                lastBx = bx;
                lastBy = by;
            }
            cx += incX;
            cy += incY;
        }
    }

    private double weightedLinearRegressionSlope(double[] x, double[] y, double[] w) {
        int n = x.length;
        double sumW = 0, sumWX = 0, sumWY = 0, sumWXX = 0, sumWXY = 0;
        for (int i = 0; i < n; i++) {
            sumW += w[i];
            sumWX += w[i] * x[i];
            sumWY += w[i] * y[i];
            sumWXX += w[i] * x[i] * x[i];
            sumWXY += w[i] * x[i] * y[i];
        }
        double denom = sumW * sumWXX - sumWX * sumWX;
        if (Math.abs(denom) < 1e-12) {
            double sumX2 = 0, sumXY2 = 0, mx = 0, my = 0;
            for (int i = 0; i < n; i++) { mx += x[i]; my += y[i]; }
            mx /= n; my /= n;
            for (int i = 0; i < n; i++) {
                sumX2 += (x[i] - mx) * (x[i] - mx);
                sumXY2 += (x[i] - mx) * (y[i] - my);
            }
            return Math.abs(sumX2) < 1e-12 ? 1.0 : sumXY2 / sumX2;
        }
        return (sumW * sumWXY - sumWX * sumWY) / denom;
    }

    private double computeInformationEntropy(List<double[][]> segments) {
        int numBins = 18;
        int[] bins = new int[numBins];
        int total = 0;
        for (double[][] seg : segments) {
            double dx = seg[1][0] - seg[0][0];
            double dy = seg[1][1] - seg[0][1];
            double len = Math.sqrt(dx * dx + dy * dy);
            int weight = (int) Math.max(1, Math.round(len * 2));
            double angle = Math.toDegrees(Math.atan2(dy, dx));
            if (angle < 0) angle += 180;
            if (angle >= 180) angle = 179.999;
            int bin = (int) (angle / 10.0);
            if (bin >= numBins) bin = numBins - 1;
            bins[bin] += weight;
            total += weight;
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

    private double computePatternSymmetry(List<double[][]> segments, double L, double W) {
        int[] quadrantCount = new int[4];
        double[] quadrantLength = new double[4];
        for (double[][] seg : segments) {
            double midX = (seg[0][0] + seg[1][0]) / 2.0;
            double midY = (seg[0][1] + seg[1][1]) / 2.0;
            boolean right = midX >= L / 2.0;
            boolean top = midY >= W / 2.0;
            int q = (top ? 2 : 0) + (right ? 1 : 0);
            double len = Math.sqrt(Math.pow(seg[1][0] - seg[0][0], 2) + Math.pow(seg[1][1] - seg[0][1], 2));
            quadrantCount[q]++;
            quadrantLength[q] += len;
        }
        double totalLen = 0;
        for (double l : quadrantLength) totalLen += l;
        if (totalLen < 1e-9) return 1.0;
        double expected = totalLen / 4.0;
        double chiSquare = 0.0;
        for (double l : quadrantLength) {
            double diff = l - expected;
            chiSquare += (diff * diff) / expected;
        }
        double horizSym = Math.abs(quadrantLength[0] + quadrantLength[3] - quadrantLength[1] - quadrantLength[2]) / totalLen;
        double vertSym = Math.abs(quadrantLength[0] + quadrantLength[1] - quadrantLength[2] - quadrantLength[3]) / totalLen;
        double distSym = 1.0 - Math.min(chiSquare / (3.0 * expected), 1.0);
        return Math.min(1.0, 0.4 * distSym + 0.3 * (1.0 - horizSym) + 0.3 * (1.0 - vertSym));
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
