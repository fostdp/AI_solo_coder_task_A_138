package com.garden.icecrack.common.repository;

import com.garden.icecrack.common.entity.Pavement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PavementRepository extends JpaRepository<Pavement, UUID> {
}
