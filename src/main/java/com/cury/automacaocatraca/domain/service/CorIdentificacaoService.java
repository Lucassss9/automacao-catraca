package com.cury.automacaocatraca.domain.service;

import com.cury.automacaocatraca.repository.EmpreiteiraCacheRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Component
public class CorIdentificacaoService {

    private static final List<String> PALETA = List.of(
            "#E53935", "#D81B60", "#8E24AA", "#5E35B1",
            "#3949AB", "#1E88E5", "#039BE5", "#00ACC1",
            "#00897B", "#43A047", "#7CB342", "#C0CA33",
            "#FDD835", "#FFB300", "#FB8C00", "#F4511E",
            "#6D4C41", "#757575", "#546E7A", "#AD1457",
            "#4527A0", "#283593", "#00695C", "#BF360C"
    );

    private final EmpreiteiraCacheRepository empreiteiraRepository;
    private final Random random = new Random();

    public CorIdentificacaoService(EmpreiteiraCacheRepository empreiteiraRepository) {
        this.empreiteiraRepository = empreiteiraRepository;
    }

    public String sortearCorDisponivel() {
        Set<String> emUso = new HashSet<>(empreiteiraRepository.buscarCoresEmUso());

        List<String> disponiveis = new ArrayList<>();
        for (String cor : PALETA) {
            if (!emUso.contains(cor)) {
                disponiveis.add(cor);
            }
        }

        if (disponiveis.isEmpty()) {
            return PALETA.get(random.nextInt(PALETA.size()));
        }

        return disponiveis.get(random.nextInt(disponiveis.size()));
    }
}