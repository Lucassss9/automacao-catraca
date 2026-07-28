package com.cury.automacaocatraca.repository;

import com.cury.automacaocatraca.domain.entity.Obra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ObraRepository extends JpaRepository<Obra, Long> {

    Optional<Obra> findByNomeNormalizado(String nomeNormalizado);

    List<Obra> findByCodigo(String codigo);
}