package com.garden.icecrack.aesthetics_analyzer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "aesthetic")
public class AestheticProperties {

    private List<Integer> unbiasedBoxSizes = List.of(2, 4, 8, 16, 32, 64, 128);
    private List<Integer> rawBoxSizes = List.of(2, 4, 8, 16, 32, 64);
    private int entropyBins = 18;
    private double entropyBinDegrees = 10.0;
    private double brownCorrectionPower = 0.15;
    private double boundaryBandRatio = 6.0;
    private double fractalWeight = 0.4;
    private double entropyWeight = 0.3;
    private double densityWeight = 0.3;
    private double densityScale = 10.0;
    private double fractalClipMin = 1.0;
    private double fractalClipMax = 2.0;
    private double fractalScaleFactor = 1.05;
    private double irregularityScale = 0.1;
    private double crackBranchHighProb = 0.7;
    private double crackBranchLowProb = 0.4;
    private double crackSplitProb = 0.35;
    private int defaultSeedPoints = 15;
    private int seedPointsVariation = 10;
    private int defaultTargetSegments = 35;
    private double defaultIrregularity = 0.7;
    private long defaultSeed = 42L;
    private double lengthWeightMultiplier = 2.0;
    private double symmetryChiDivisor = 3.0;
    private double symmetryDistWeight = 0.4;
    private double symmetryHorizWeight = 0.3;
    private double symmetryVertWeight = 0.3;
}
