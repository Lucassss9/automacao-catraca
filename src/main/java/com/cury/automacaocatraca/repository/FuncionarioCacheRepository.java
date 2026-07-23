package com.cury.automacaocatraca.repository;

import com.cury.automacaocatraca.domain.entity.FuncionarioCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FuncionarioCacheRepository extends JpaRepository<FuncionarioCache, Long> {

    Optional<FuncionarioCache> findByCpf(String cpf);

    Optional<FuncionarioCache> findByNomeNormalizadoAndEmpreiteiraNomeNormalizado(
            String nomeNormalizado, String empreiteiraNomeNormalizado);
}