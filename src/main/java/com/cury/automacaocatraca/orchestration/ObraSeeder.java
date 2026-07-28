package com.cury.automacaocatraca.orchestration;

import com.cury.automacaocatraca.config.ObraConfig;
import com.cury.automacaocatraca.config.ObrasRegistry;
import com.cury.automacaocatraca.domain.entity.Obra;
import com.cury.automacaocatraca.domain.util.NormalizadorNome;
import com.cury.automacaocatraca.repository.ObraRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ObraSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ObraSeeder.class);

    private final ObrasRegistry registry;
    private final ObraRepository obraRepository;

    public ObraSeeder(ObrasRegistry registry, ObraRepository obraRepository) {
        this.registry = registry;
        this.obraRepository = obraRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (registry.obras() == null || registry.obras().isEmpty()) {
            log.warn("Nenhuma obra configurada em automacao.obras");
            return;
        }

        int novas = 0;

        for (ObraConfig config : registry.obras()) {
            for (String nomeCfObras : config.nomesCfObras()) {
                if (gravar(config, nomeCfObras)) {
                    novas++;
                }
            }
        }

        log.info("Obras no banco: {} ({} criadas agora)", obraRepository.count(), novas);
    }

    private boolean gravar(ObraConfig config, String nomeCfObras) {
        String normalizado = NormalizadorNome.normalizar(nomeCfObras);

        boolean nova = obraRepository.findByNomeNormalizado(normalizado).isEmpty();

        Obra obra = obraRepository.findByNomeNormalizado(normalizado).orElseGet(Obra::new);

        obra.setCodigo(config.codigo());
        obra.setNome(nomeCfObras);
        obra.setNomeNormalizado(normalizado);
        obra.setDataVerificacao(LocalDateTime.now());

        obraRepository.save(obra);

        if (nova) {
            log.info("Obra cadastrada: {} ({})", nomeCfObras, config.codigo());
        }

        return nova;
    }
}