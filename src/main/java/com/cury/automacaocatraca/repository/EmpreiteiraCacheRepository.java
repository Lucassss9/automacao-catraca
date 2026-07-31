package com.cury.automacaocatraca.repository;

import com.cury.automacaocatraca.domain.entity.EmpreiteiraCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EmpreiteiraCacheRepository extends JpaRepository<EmpreiteiraCache, Long> {

    Optional<EmpreiteiraCache> findByNomeNormalizado(String nomeNormalizado);

    Optional<EmpreiteiraCache> findByCnpj(String cnpj);

    List<EmpreiteiraCache> findAllByIdCfObras(String idCfObras);

    @Query("select e.corIdentificacao from EmpreiteiraCache e where e.corIdentificacao is not null")
    List<String> buscarCoresEmUso();
}