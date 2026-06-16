package com.garden.icecrack.repository;

import com.garden.icecrack.entity.Pavement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PavementRepository extends JpaRepository<Pavement, UUID> {
}
