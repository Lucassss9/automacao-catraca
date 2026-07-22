package com.cury.automacaocatraca.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "empreiteira_cache")
@Getter
@Setter
@NoArgsConstructor
public class EmpreiteiraCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_trc", nullable = false)
    private String nomeTrc;

    @Column(name = "nome_normalizado", nullable = false, unique = true)
    private String nomeNormalizado;

    @Column(name = "cnpj", unique = true)
    private String cnpj;

    @Column(name = "razao_social")
    private String razaoSocial;

    @Column(name = "nome_fantasia")
    private String nomeFantasia;

    @Column(name = "cadastrada_no_cf_obras", nullable = false)
    private boolean cadastradaNoCfObras;

    @Column(name = "data_ultima_verificacao")
    private LocalDateTime dataUltimaVerificacao;
}