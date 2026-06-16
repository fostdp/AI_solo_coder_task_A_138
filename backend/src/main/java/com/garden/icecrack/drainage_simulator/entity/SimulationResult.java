package com.garden.icecrack.drainage_simulator.entity;

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
@Table(name = "simulation_result")
public class SimulationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Pavement pavement;

    private Long sensorDataId;

    private LocalDateTime simTime;

    private Double initialWaterDepth;

    private Double recessionTimeSec;

    private Double peakWaterDepth;

    private Double drainageRate;

    private Double infiltrationRate;

    private Double surfaceRunoffRate;

    @Column(columnDefinition = "jsonb")
    private String timeSeries;

    @Column(columnDefinition = "jsonb")
    private String gridData;

    private Boolean alertTriggered;

    private String alertMessage;
}
