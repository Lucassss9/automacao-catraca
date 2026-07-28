package com.cury.automacaocatraca.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "empreiteira_obra_vinculo",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vinculo_empreiteira_obra",
                columnNames = {"empreiteira_id", "obra_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class EmpreiteiraObraVinculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "empreiteira_id", nullable = false)
    private EmpreiteiraCache empreiteira;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "obra_id", nullable = false)
    private Obra obra;

    @Column(name = "data_verificacao")
    private LocalDateTime dataVerificacao;
}