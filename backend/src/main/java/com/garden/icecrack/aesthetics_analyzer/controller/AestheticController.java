package com.garden.icecrack.aesthetics_analyzer.controller;

import com.garden.icecrack.aesthetics_analyzer.dto.AestheticResultDTO;
import com.garden.icecrack.aesthetics_analyzer.service.AestheticQuantificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/aesthetic")
@RequiredArgsConstructor
public class AestheticController {

    private final AestheticQuantificationService aestheticQuantificationService;

    @PostMapping("/analyze/{pavementId}")
    public ResponseEntity<AestheticResultDTO> analyze(@PathVariable UUID pavementId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(aestheticQuantificationService.analyzePavement(pavementId));
    }

    @GetMapping("/{pavementId}/history")
    public ResponseEntity<List<AestheticResultDTO>> history(@PathVariable UUID pavementId) {
        return ResponseEntity.ok(aestheticQuantificationService.getAnalysisHistory(pavementId));
    }
}
