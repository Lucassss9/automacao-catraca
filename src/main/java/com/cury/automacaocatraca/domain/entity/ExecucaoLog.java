package com.cury.automacaocatraca.domain.entity;

import com.cury.automacaocatraca.domain.enums.StatusExecucao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "execucao_log")
@Getter
@Setter
@NoArgsConstructor
public class ExecucaoLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_obra", nullable = false)
    private String codigoObra;

    @Column(name = "data_execucao", nullable = false)
    private LocalDateTime dataExecucao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusExecucao status;

    @Column(name = "empreiteiras_cadastradas")
    private int empreiteirasCadastradas;

    @Column(name = "funcionarios_cadastrados")
    private int funcionariosCadastrados;

    @Column(name = "funcoes_desconhecidas", length = 2000)
    private String funcoesDesconhecidas;

    @Column(name = "atribuicoes_gestor", length = 2000)
    private String atribuicoesGestor;

    @Column(name = "mensagem_erro", length = 4000)
    private String mensagemErro;
}