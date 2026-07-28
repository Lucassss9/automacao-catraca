package com.cury.automacaocatraca.domain.mapper;

import com.cury.automacaocatraca.domain.dto.DadosCadastroEmpreiteira;
import com.cury.automacaocatraca.domain.dto.DadosCadastroFuncionario;
import com.cury.automacaocatraca.domain.enums.NivelAcesso;
import com.cury.automacaocatraca.domain.util.NormalizadorNome;
import com.cury.automacaocatraca.trc.dto.EmpreiteiraTrc;
import com.cury.automacaocatraca.trc.dto.FuncionarioTrc;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CadastroMapper {

    private static final String TELEFONE_PADRAO = "(00) 00000-0000";
    private static final String DOMINIO_EMAIL = ".com";

    public DadosCadastroEmpreiteira mapear(EmpreiteiraTrc origem, String cor) {
        String telefone = telefoneOuPadrao(origem.contatoTelefone());
        String whatsapp = telefoneOuPadrao(origem.contatoCelular());
        String email = emailOuPadrao(origem.contatoEmail(), origem.razaoSocial());

        return new DadosCadastroEmpreiteira(
                origem.razaoSocial(),
                origem.nomeFantasia(),
                origem.cnpj(),
                telefone,
                whatsapp,
                email,
                cor,
                BigDecimal.ZERO
        );
    }

    public DadosCadastroFuncionario mapear(FuncionarioTrc origem) {
        return new DadosCadastroFuncionario(
                NormalizadorNome.proprio(origem.nome()),
                origem.cpf(),
                origem.rg(),
                origem.dataNascimento(),
                telefoneOuPadrao(origem.telefone()),
                emailOuPadrao(origem.email(), origem.nome()),
                origem.funcao(),
                origem.entradaNaObra(),
                nivelPorFuncao(origem.funcao())
        );
    }

    private String telefoneOuPadrao(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return TELEFONE_PADRAO;
        }
        return telefone;
    }

    private String emailOuPadrao(String email, String nomeCompleto) {
        if (email != null && !email.isBlank()) {
            return email;
        }

        String normalizado = NormalizadorNome.normalizar(nomeCompleto);
        if (normalizado.isBlank()) {
            return "semnome@semnome" + DOMINIO_EMAIL;
        }

        String[] partes = normalizado.split("\\s+");
        String primeiro = partes[0].toLowerCase();
        String ultimo = partes[partes.length - 1].toLowerCase();

        return primeiro + "@" + ultimo + DOMINIO_EMAIL;
    }

    public NivelAcesso nivelPorFuncao(String funcao) {
        String chave = NormalizadorNome.normalizar(funcao);

        if (chave.contains("PROPRIETARIO") || chave.contains("GERENTE")) {
            return NivelAcesso.GESTOR;
        }
        if (chave.contains("ENCARREGADO")) {
            return NivelAcesso.ENCARREGADO;
        }
        return NivelAcesso.FUNCIONARIO;
    }
}