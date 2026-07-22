package com.cury.automacaocatraca.repository;

import com.cury.automacaocatraca.domain.entity.ExecucaoLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecucaoLogRepository extends JpaRepository<ExecucaoLog, Long> {
}