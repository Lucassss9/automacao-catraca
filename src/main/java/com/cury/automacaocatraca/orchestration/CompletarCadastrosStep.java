package com.cury.automacaocatraca.orchestration;

import com.cury.automacaocatraca.cfobras.page.CompletarCadastrosPage;
import com.cury.automacaocatraca.domain.dto.DadosCadastroFuncionario;
import com.cury.automacaocatraca.domain.mapper.CadastroMapper;
import com.cury.automacaocatraca.trc.TrcFuncionarioExtractor;
import com.cury.automacaocatraca.trc.dto.FuncionarioTrc;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class CompletarCadastrosStep {

    private static final Logger log = LoggerFactory.getLogger(CompletarCadastrosStep.class);

    private static final int TETO_DE_VOLTAS = 400;

    private final CompletarCadastrosPage pagina;
    private final TrcFuncionarioExtractor trcExtractor;
    private final CadastroMapper cadastroMapper;

    public CompletarCadastrosStep(CompletarCadastrosPage pagina,
                                  TrcFuncionarioExtractor trcExtractor,
                                  CadastroMapper cadastroMapper) {
        this.pagina = pagina;
        this.trcExtractor = trcExtractor;
        this.cadastroMapper = cadastroMapper;
    }

    public static final class Resumo {
        public final List<String> completados = new ArrayList<>();
        public final List<String> semDadosNoTrc = new ArrayList<>();
        public final List<String> falhas = new ArrayList<>();
        public String progressoInicial = "";
        public String progressoFinal = "";

        public String resumoTexto() {
            return String.format("Completar cadastros — completados: %d | sem dados no TRC: %d | falhas: %d",
                    completados.size(), semDadosNoTrc.size(), falhas.size());
        }
    }

    public Resumo executar(WebDriver trc, WebDriver cf) {
        Resumo resumo = new Resumo();

        pagina.abrir(cf);

        if (!pagina.temLista(cf)) {
            log.warn("Aba 'Completar Cadastros' nao disponivel nesta sessao");
            return resumo;
        }

        resumo.progressoInicial = pagina.progresso(cf);

        if (pagina.filaVazia(cf)) {
            log.info("Fila de cadastros incompletos ja esta zerada");
            return resumo;
        }

        log.info("Fila de cadastros incompletos: {}", resumo.progressoInicial);

        Set<String> jaTentados = new HashSet<>();

        for (int volta = 0; volta < TETO_DE_VOLTAS; volta++) {
            List<CompletarCadastrosPage.Pendente> fila = pagina.listar(cf);

            CompletarCadastrosPage.Pendente alvo = fila.stream()
                    .filter(p -> !jaTentados.contains(p.id()))
                    .findFirst()
                    .orElse(null);

            if (alvo == null) {
                break;
            }

            jaTentados.add(alvo.id());
            processar(trc, cf, alvo, resumo);
        }

        resumo.progressoFinal = pagina.progresso(cf);

        log.info("{} | progresso: '{}' -> '{}'",
                resumo.resumoTexto(), resumo.progressoInicial, resumo.progressoFinal);

        return resumo;
    }

    private void processar(WebDriver trc, WebDriver cf,
                           CompletarCadastrosPage.Pendente alvo, Resumo resumo) {

        if (!pagina.selecionar(cf, alvo)) {
            resumo.falhas.add(alvo.nome() + ": nao consegui abrir o formulario");
            return;
        }

        Optional<TrcFuncionarioExtractor.LinhaTrc> noTrc =
                trcExtractor.localizarNaListagem(trc, alvo.nome());

        if (noTrc.isEmpty()) {
            log.warn("[SEM DADOS] {} ({}) — nao esta na listagem do TRC", alvo.nome(), alvo.empreiteiro());
            resumo.semDadosNoTrc.add(alvo.nome() + " (" + alvo.empreiteiro() + ")");
            pagina.pular(cf);
            return;
        }

        TrcFuncionarioExtractor.LinhaTrc linha = noTrc.get();

        DadosCadastroFuncionario dados = cadastroMapper.mapear(new FuncionarioTrc(
                linha.nome(),
                linha.cpf(),
                "",
                null,
                linha.funcao(),
                "",
                "",
                linha.empreiteira(),
                null));

        log.info("[COMPLETANDO] {} | falta {} | CPF {} | funcao '{}'",
                alvo.nome(), alvo.faltando(), dados.cpf(), dados.funcao());

        pagina.completar(cf, dados);

        String mensagem = pagina.salvarEProximo(cf);

        if (indicaErro(mensagem)) {
            log.warn("[FALHOU] {} -> {}", alvo.nome(), mensagem);
            resumo.falhas.add(alvo.nome() + ": " + mensagem);
            return;
        }

        log.info("[OK] {} -> {}", alvo.nome(), mensagem);
        resumo.completados.add(alvo.nome() + " (" + alvo.empreiteiro() + ")");
    }

    private boolean indicaErro(String mensagem) {
        String texto = mensagem == null ? "" : mensagem.toLowerCase();

        return texto.contains("erro") || texto.contains("obrigat")
                || texto.contains("falha") || texto.contains("invalid")
                || texto.contains("preencha");
    }
}