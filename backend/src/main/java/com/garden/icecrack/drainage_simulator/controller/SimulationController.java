package com.garden.icecrack.drainage_simulator.controller;

import com.garden.icecrack.drainage_simulator.dto.SimulationRequestDTO;
import com.garden.icecrack.drainage_simulator.dto.SimulationResultDTO;
import com.garden.icecrack.drainage_simulator.service.DrainageSimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final DrainageSimulationService drainageSimulationService;

    @PostMapping("/run")
    public ResponseEntity<SimulationResultDTO> run(@RequestBody SimulationRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(drainageSimulationService.runSimulation(request));
    }

    @GetMapping("/{pavementId}/history")
    public ResponseEntity<List<SimulationResultDTO>> history(@PathVariable UUID pavementId) {
        return ResponseEntity.ok(drainageSimulationService.getSimulationHistory(pavementId));
    }
}
