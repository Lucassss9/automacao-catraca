package com.cury.automacaocatraca.repository;

import com.cury.automacaocatraca.domain.entity.EmpreiteiraCache;
import com.cury.automacaocatraca.domain.entity.EmpreiteiraObraVinculo;
import com.cury.automacaocatraca.domain.entity.Obra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmpreiteiraObraVinculoRepository extends JpaRepository<EmpreiteiraObraVinculo, Long> {

    Optional<EmpreiteiraObraVinculo> findByEmpreiteiraAndObra(EmpreiteiraCache empreiteira, Obra obra);

    List<EmpreiteiraObraVinculo> findByEmpreiteira(EmpreiteiraCache empreiteira);

    List<EmpreiteiraObraVinculo> findByObra(Obra obra);
}