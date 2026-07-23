package com.cury.automacaocatraca.domain.mapper;

import com.cury.automacaocatraca.domain.dto.DadosCadastroEmpreiteira;
import com.cury.automacaocatraca.domain.dto.DadosCadastroFuncionario;
import com.cury.automacaocatraca.domain.enums.NivelAcesso;
import com.cury.automacaocatraca.trc.dto.EmpreiteiraTrc;
import com.cury.automacaocatraca.trc.dto.FuncionarioTrc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CadastroMapperTest {

    private final CadastroMapper mapper = new CadastroMapper();

    @Test
    @DisplayName("funcionario sem telefone recebe o telefone padrao zerado")
    void funcionarioSemTelefoneRecebePadrao() {
        FuncionarioTrc origem = funcionarioCom("Marco Antonio De Santana", "", "", "Porteiro");

        DadosCadastroFuncionario resultado = mapper.mapear(origem);

        assertEquals("(00) 00000-0000", resultado.telefone());
    }

    @Test
    @DisplayName("funcionario com telefone preenchido mantem o telefone do TRC")
    void funcionarioComTelefoneMantemOriginal() {
        FuncionarioTrc origem = funcionarioCom("Marco Antonio De Santana", "(11) 98888-7777", "", "Porteiro");

        DadosCadastroFuncionario resultado = mapper.mapear(origem);

        assertEquals("(11) 98888-7777", resultado.telefone());
    }

    @Test
    @DisplayName("funcionario sem email recebe primeironome arroba ultimonome")
    void funcionarioSemEmailRecebeEmailGerado() {
        FuncionarioTrc origem = funcionarioCom("Marco Antonio De Santana", "", "", "Porteiro");

        DadosCadastroFuncionario resultado = mapper.mapear(origem);

        assertEquals("marco@santana.com", resultado.email());
    }

    @Test
    @DisplayName("email gerado ignora acentos e sujeira de encoding do nome")
    void emailGeradoIgnoraAcentos() {
        FuncionarioTrc origem = funcionarioCom("JoÃo Victor Lima Batista", "", "", "Pedreiro");

        DadosCadastroFuncionario resultado = mapper.mapear(origem);

        assertEquals("joao@batista.com", resultado.email());
    }

    @Test
    @DisplayName("nome com uma palavra so gera email com a mesma palavra dos dois lados")
    void emailDeNomeComUmaPalavra() {
        FuncionarioTrc origem = funcionarioCom("Madonna", "", "", "Servente");

        DadosCadastroFuncionario resultado = mapper.mapear(origem);

        assertEquals("madonna@madonna.com", resultado.email());
    }

    @Test
    @DisplayName("funcionario com email preenchido mantem o email do TRC")
    void funcionarioComEmailMantemOriginal() {
        FuncionarioTrc origem = funcionarioCom("Marco Antonio", "", "marco@empresa.com.br", "Porteiro");

        DadosCadastroFuncionario resultado = mapper.mapear(origem);

        assertEquals("marco@empresa.com.br", resultado.email());
    }

    @Test
    @DisplayName("funcao com proprietario vira nivel gestor")
    void funcaoProprietarioViraGestor() {
        assertEquals(NivelAcesso.GESTOR, mapper.nivelPorFuncao("Proprietário"));
    }

    @Test
    @DisplayName("funcao com gerente vira nivel gestor")
    void funcaoGerenteViraGestor() {
        assertEquals(NivelAcesso.GESTOR, mapper.nivelPorFuncao("Gerente de Obra"));
    }

    @Test
    @DisplayName("funcao com encarregado vira nivel encarregado")
    void funcaoEncarregadoViraEncarregado() {
        assertEquals(NivelAcesso.ENCARREGADO, mapper.nivelPorFuncao("Encarregado de Obra"));
    }

    @Test
    @DisplayName("gerente encarregado cai em gestor por causa da ordem das regras")
    void gerenteEncarregadoCaiEmGestor() {
        assertEquals(NivelAcesso.GESTOR, mapper.nivelPorFuncao("Gerente Encarregado"));
    }

    @Test
    @DisplayName("funcao desconhecida cai no nivel funcionario")
    void funcaoDesconhecidaViraFuncionario() {
        assertEquals(NivelAcesso.FUNCIONARIO, mapper.nivelPorFuncao("Servente de Obra"));
    }

    @Test
    @DisplayName("funcao nula ou vazia cai no nivel funcionario")
    void funcaoNulaViraFuncionario() {
        assertEquals(NivelAcesso.FUNCIONARIO, mapper.nivelPorFuncao(null));
        assertEquals(NivelAcesso.FUNCIONARIO, mapper.nivelPorFuncao("   "));
    }

    @Test
    @DisplayName("empreiteira cadastrada pelo robo recebe valor unitario zero e a cor informada")
    void empreiteiraRecebeValorZeroECorInformada() {
        EmpreiteiraTrc origem = new EmpreiteiraTrc(
                "ZELO ASSESSORIA PORTARIA E LIMPEZA LTDA",
                "12.345.678/0001-90",
                "Zelo",
                "Contato",
                "",
                "",
                ""
        );

        DadosCadastroEmpreiteira resultado = mapper.mapear(origem, "#1E88E5");

        assertEquals(BigDecimal.ZERO, resultado.valorUnitario());
        assertEquals("#1E88E5", resultado.corIdentificacao());
        assertEquals("(00) 00000-0000", resultado.telefone());
        assertEquals("(00) 00000-0000", resultado.whatsapp());
    }

    private FuncionarioTrc funcionarioCom(String nome, String telefone, String email, String funcao) {
        return new FuncionarioTrc(
                nome,
                "123.456.789-00",
                "12.345.678-9",
                LocalDate.of(1990, 1, 1),
                funcao,
                telefone,
                email,
                "ZELO ASSESSORIA PORTARIA E LIMPEZA LTDA",
                LocalDate.of(2026, 1, 15)
        );
    }
}