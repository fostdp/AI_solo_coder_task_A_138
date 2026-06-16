package com.garden.icecrack.drainage_simulator.repository;

import com.garden.icecrack.drainage_simulator.entity.SimulationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SimulationResultRepository extends JpaRepository<SimulationResult, Long> {

    List<SimulationResult> findByPavementIdOrderBySimTimeDesc(UUID pavementId);
}
