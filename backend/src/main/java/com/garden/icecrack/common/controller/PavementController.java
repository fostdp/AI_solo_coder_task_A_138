package com.garden.icecrack.common.controller;

import com.garden.icecrack.common.entity.Pavement;
import com.garden.icecrack.common.repository.PavementRepository;
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
@RequestMapping("/api/pavements")
@RequiredArgsConstructor
public class PavementController {

    private final PavementRepository pavementRepository;

    @GetMapping
    public ResponseEntity<List<Pavement>> listAll() {
        return ResponseEntity.ok(pavementRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pavement> getById(@PathVariable UUID id) {
        return pavementRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Pavement> create(@RequestBody Pavement pavement) {
        Pavement saved = pavementRepository.save(pavement);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
