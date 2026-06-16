package com.garden.icecrack.repository;

import com.garden.icecrack.entity.AestheticResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AestheticResultRepository extends JpaRepository<AestheticResult, Long> {

    List<AestheticResult> findByPavementIdOrderByCalcTimeDesc(UUID pavementId);
}
