package com.garden.icecrack.aesthetics_analyzer.entity;

import com.garden.icecrack.common.entity.Pavement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "aesthetic_result")
public class AestheticResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Pavement pavement;

    private LocalDateTime calcTime;

    private Double fractalDimension;

    private Double boxCountingDim;

    private Double infoEntropy;

    private Double visualComplexity;

    private Integer crackCount;

    private Double avgCrackLength;

    private Double crackDensity;

    private Double patternSymmetry;

    @Column(columnDefinition = "jsonb")
    private String crackSegments;
}
