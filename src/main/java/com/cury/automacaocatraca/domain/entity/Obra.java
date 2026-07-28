package com.cury.automacaocatraca.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "obra")
@Getter
@Setter
@NoArgsConstructor
public class Obra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", length = 60)
    private String codigo;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "nome_normalizado", nullable = false, unique = true)
    private String nomeNormalizado;

    @Column(name = "id_cf_obras", length = 80)
    private String idCfObras;

    @Column(name = "data_verificacao")
    private LocalDateTime dataVerificacao;
}