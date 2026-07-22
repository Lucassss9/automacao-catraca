package com.cury.automacaocatraca.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "funcionario_cache",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_funcionario_nome_empreiteira",
                columnNames = {"nome_normalizado", "empreiteira_cnpj"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class FuncionarioCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_trc", nullable = false)
    private String nomeTrc;

    @Column(name = "nome_normalizado", nullable = false)
    private String nomeNormalizado;

    @Column(name = "cpf", unique = true)
    private String cpf;

    @Column(name = "funcao")
    private String funcao;

    @Column(name = "empreiteira_cnpj")
    private String empreiteiraCnpj;

    @Column(name = "cadastrado_no_cf_obras", nullable = false)
    private boolean cadastradoNoCfObras;

    @Column(name = "data_ultima_verificacao")
    private LocalDateTime dataUltimaVerificacao;
}