package com.cury.automacaocatraca.repository;

import com.cury.automacaocatraca.domain.entity.EmpreiteiraCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmpreiteiraCacheRepository extends JpaRepository<EmpreiteiraCache, Long> {

    Optional<EmpreiteiraCache> findByNomeNormalizado(String nomeNormalizado);

    Optional<EmpreiteiraCache> findByCnpj(String cnpj);
}