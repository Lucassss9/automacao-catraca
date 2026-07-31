package com.cury.automacaocatraca.trc;

import com.cury.automacaocatraca.config.TrcProperties;
import com.cury.automacaocatraca.domain.util.NormalizadorNome;
import com.cury.automacaocatraca.trc.dto.EmpreiteiraTrc;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class TrcEmployeeExtractor {

    private static final Logger log = LoggerFactory.getLogger(TrcEmployeeExtractor.class);

    private static final By TABELA = By.id("table1");
    private static final By CAMPO_RAZAO_SOCIAL = By.id("razao_social");

    private final Set<String> idsJaUsados = new HashSet<>();
    private final Map<String, String> cnpjPorNome = new LinkedHashMap<>();
    private boolean listagemCarregada;

    private final TrcProperties properties;
    private final TrcAuthenticator authenticator;

    public TrcEmployeeExtractor(TrcProperties properties, TrcAuthenticator authenticator) {
        this.properties = properties;
        this.authenticator = authenticator;
    }

    public void limparCache() {
        idsJaUsados.clear();
        cnpjPorNome.clear();
        listagemCarregada = false;
    }

    public Optional<String> cnpjDe(WebDriver driver, String nome) {
        garantirListagem(driver);

        String alvo = NormalizadorNome.normalizar(nome);
        String cnpj = cnpjPorNome.get(alvo);

        if (cnpj != null && !cnpj.isBlank()) {
            return Optional.of(cnpj);
        }

        String alvoC = alvo.replaceAll("[^A-Z0-9]", "");

        for (Map.Entry<String, String> item : cnpjPorNome.entrySet()) {
            String chaveC = item.getKey().replaceAll("[^A-Z0-9]", "");

            if (chaveC.equals(alvoC)
                    || (alvoC.length() > 6 && chaveC.length() > 6
                    && (chaveC.contains(alvoC) || alvoC.contains(chaveC)))) {
                return Optional.of(item.getValue());
            }
        }

        return Optional.empty();
    }

    private void garantirListagem(WebDriver driver) {
        if (listagemCarregada) {
            return;
        }

        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            try {
                driver.get(baseUrl() + "/home/empreiteira");
                authenticator.fecharModais(driver);
                aguardarTabela(driver);

                Object bruto = executar(driver,
                        "function norm(t){ return (t||'').replace(/\\u00a0/g,' ').normalize('NFD')"
                                + "  .replace(/[\\u0300-\\u036f]/g,'')"
                                + "  .toUpperCase().replace(/\\s+/g,' ').trim(); }"
                                + "var linhas = document.querySelectorAll('#table1 tbody tr');"
                                + "var saida = [];"
                                + "for (var i = 0; i < linhas.length; i++) {"
                                + "  var c = linhas[i].cells;"
                                + "  if (!c || c.length < 2) { continue; }"
                                + "  var nome = norm(c[0].textContent);"
                                + "  var cnpj = (c[1].textContent || '').replace(/\\D/g,'');"
                                + "  if (nome && cnpj.length === 14) { saida.push([nome, cnpj]); }"
                                + "}"
                                + "return saida;");

                if (bruto instanceof java.util.List<?> lista && !lista.isEmpty()) {
                    for (Object item : lista) {
                        if (item instanceof java.util.List<?> par && par.size() == 2) {
                            cnpjPorNome.put(String.valueOf(par.get(0)), String.valueOf(par.get(1)));
                        }
                    }

                    listagemCarregada = true;
                    log.info("Listagem de empreiteiras do TRC em cache: {} com CNPJ", cnpjPorNome.size());
                    return;
                }

                log.warn("Listagem de empreiteiras veio vazia (tentativa {}/3)", tentativa);

            } catch (Exception e) {
                log.warn("Falha ao carregar a listagem de empreiteiras (tentativa {}/3): {}",
                        tentativa, e.getMessage());
            }

            esperar(1500);
        }

        log.error("Nao consegui carregar a listagem de empreiteiras do TRC");
    }

    public Optional<EmpreiteiraTrc> extrairEmpreiteira(WebDriver driver, String nomeBusca) {
        try {
            String id = localizar(driver, nomeBusca);

            if (id == null) {
                log.warn("Empreiteira '{}' nao encontrada no TRC", nomeBusca);
                return Optional.empty();
            }

            return Optional.of(lerTelaDeEdicao(driver, id, nomeBusca));

        } catch (Exception e) {
            log.error("Erro ao buscar '{}' no TRC: {}", nomeBusca, e.getMessage());
            return Optional.empty();
        }
    }

    private String localizar(WebDriver driver, String nomeBusca) {
        driver.get(baseUrl() + "/home/empreiteira");
        authenticator.fecharModais(driver);
        aguardarTabela(driver);

        String id = procurarNaTabela(driver, nomeBusca, "listagem completa");
        if (id != null) {
            idsJaUsados.add(id);
            return id;
        }

        String termo = primeiraPalavraSignificativa(nomeBusca);
        submeterBusca(driver, termo);

        id = procurarNaTabela(driver, nomeBusca, "resultado da busca");
        if (id != null) {
            idsJaUsados.add(id);
            return id;
        }

        id = unicoResultadoDaBusca(driver, nomeBusca, termo);
        if (id != null) {
            idsJaUsados.add(id);
        }

        return id;
    }

    private String unicoResultadoDaBusca(WebDriver driver, String nomeBusca, String termo) {
        Object resultado = executar(driver,
                "var linhas = document.querySelectorAll('#table1 tbody tr');"
                        + "var achados = [];"
                        + "for (var i = 0; i < linhas.length; i++) {"
                        + "  var link = linhas[i].querySelector(\"a[href*='alterEmpreiteira']\");"
                        + "  if (!link) { continue; }"
                        + "  var href = link.getAttribute('href') || '';"
                        + "  achados.push({"
                        + "    id: href.substring(href.lastIndexOf('/') + 1),"
                        + "    nome: (linhas[i].cells[0].textContent || '').replace(/\\s+/g,' ').trim()"
                        + "  });"
                        + "}"
                        + "return achados;");

        if (!(resultado instanceof List<?> lista)) {
            return null;
        }

        List<Map<?, ?>> candidatos = new ArrayList<>();

        for (Object item : lista) {
            if (!(item instanceof Map<?, ?> linha)) {
                continue;
            }
            if (idsJaUsados.contains(String.valueOf(linha.get("id")))) {
                continue;
            }
            candidatos.add(linha);
        }

        if (candidatos.size() != 1) {
            log.warn("Busca por '{}' devolveu {} empreiteiras livres ({} no total) "
                            + "— nao da para escolher sozinho",
                    termo, candidatos.size(), lista.size());
            return null;
        }

        Map<?, ?> unica = candidatos.get(0);

        log.info("'{}' aceita como '{}' — unica empreiteira livre na busca por '{}' (id {})",
                nomeBusca, unica.get("nome"), termo, unica.get("id"));

        return String.valueOf(unica.get("id"));
    }

    private void submeterBusca(WebDriver driver, String termo) {
        log.info("Filtrando a listagem do TRC por '{}'", termo);

        executar(driver,
                "var campo = document.getElementById('nome_empreiteira');"
                        + "if (!campo) { return; }"
                        + "campo.value = arguments[0];"
                        + "campo.dispatchEvent(new Event('input', {bubbles:true}));"
                        + "var form = campo.form || document.forms['busca_empreiteira'];"
                        + "if (form) { form.submit(); }",
                termo);

        esperar(2500);
        aguardarTabela(driver);
        authenticator.fecharModais(driver);
    }

    private void aguardarTabela(WebDriver driver) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(20))
                    .until(ExpectedConditions.presenceOfElementLocated(TABELA));
        } catch (Exception e) {
            log.warn("Tabela de empreiteiras nao apareceu");
        }
    }

    private String procurarNaTabela(WebDriver driver, String nomeBusca, String origem) {
        String alvo = NormalizadorNome.normalizar(nomeBusca);
        String alvoCompacto = compactar(alvo);

        Object resultado = executar(driver,
                "var alvo = arguments[0] || '';"
                        + "var alvoCompacto = arguments[1] || '';"
                        + "function norm(t){ return (t||'').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'')"
                        + "  .toUpperCase().replace(/\\s+/g,' ').trim(); }"
                        + "function compact(t){ return norm(t).replace(/[^A-Z0-9]/g,''); }"
                        + "var linhas = document.querySelectorAll('#table1 tbody tr');"
                        + "var exato = null, compacto = null, parcial = null;"
                        + "for (var i = 0; i < linhas.length; i++) {"
                        + "  var tds = linhas[i].cells;"
                        + "  if (!tds || tds.length < 2) { continue; }"
                        + "  var link = linhas[i].querySelector(\"a[href*='alterEmpreiteira']\");"
                        + "  if (!link) { continue; }"
                        + "  var href = link.getAttribute('href') || '';"
                        + "  var id = href.substring(href.lastIndexOf('/') + 1);"
                        + "  var nome = norm(tds[0].textContent);"
                        + "  var nc = compact(tds[0].textContent);"
                        + "  if (alvo && nome === alvo) { exato = id; break; }"
                        + "  if (compacto === null && alvoCompacto && nc === alvoCompacto) { compacto = id; }"
                        + "  if (parcial === null && nc.length > 6 && alvoCompacto.length > 6"
                        + "      && (nc.indexOf(alvoCompacto) >= 0 || alvoCompacto.indexOf(nc) >= 0)) { parcial = id; }"
                        + "}"
                        + "var achado = exato || compacto || parcial;"
                        + "return {"
                        + "  id: achado,"
                        + "  tipo: exato ? 'exato' : (compacto ? 'compacto' : (parcial ? 'parcial' : 'nenhum')),"
                        + "  linhas: linhas.length"
                        + "};",
                alvo, alvoCompacto);

        Map<?, ?> mapa = (Map<?, ?>) resultado;
        Object id = mapa.get("id");
        Object tipo = mapa.get("tipo");
        Object linhas = mapa.get("linhas");

        if (id == null) {
            log.info("'{}' nao encontrada na {} ({} linhas varridas)", nomeBusca, origem, linhas);
            return null;
        }

        log.info("'{}' encontrada na {} por match {} (id {}, {} linhas)",
                nomeBusca, origem, tipo, id, linhas);
        return String.valueOf(id);
    }

    private EmpreiteiraTrc lerTelaDeEdicao(WebDriver driver, String id, String nomeBusca) {
        driver.get(baseUrl() + "/home/alterEmpreiteira/" + id);
        authenticator.fecharModais(driver);

        new WebDriverWait(driver, Duration.ofSeconds(20))
                .until(ExpectedConditions.presenceOfElementLocated(CAMPO_RAZAO_SOCIAL));

        EmpreiteiraTrc dados = new EmpreiteiraTrc(
                valorDe(driver, "razao_social"),
                valorDe(driver, "cnpj"),
                valorDe(driver, "nome_fantasia"),
                valorDe(driver, "nome_contato"),
                valorDe(driver, "telefone"),
                valorDe(driver, "celular"),
                primeiroEmailTagit(driver));

        log.info("Dados lidos de '{}': CNPJ={}, fantasia='{}', email='{}'",
                nomeBusca, dados.cnpj(), dados.nomeFantasia(), dados.contatoEmail());

        return dados;
    }

    private String valorDe(WebDriver driver, String id) {
        Object valor = executar(driver,
                "var el = document.getElementById(arguments[0]);"
                        + "return el ? (el.value || '') : '';",
                id);
        return String.valueOf(valor).trim();
    }

    private String primeiroEmailTagit(WebDriver driver) {
        Object valor = executar(driver,
                "var hid = document.querySelectorAll('#email .tagit-hidden-field');"
                        + "for (var i = 0; i < hid.length; i++) {"
                        + "  if (hid[i].value && hid[i].value.trim() !== '') { return hid[i].value.trim(); }"
                        + "}"
                        + "return '';");
        return String.valueOf(valor).trim();
    }

    private String compactar(String texto) {
        return texto == null ? "" : texto.replaceAll("[^A-Z0-9]", "");
    }

    private String primeiraPalavraSignificativa(String nome) {
        String[] partes = NormalizadorNome.normalizar(nome).split("\\s+");
        for (String parte : partes) {
            String limpa = parte.replaceAll("[^A-Z0-9]", "");
            if (limpa.length() >= 3) {
                return limpa;
            }
        }
        return partes.length > 0 ? partes[0] : nome;
    }

    private Object executar(WebDriver driver, String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }

    private String baseUrl() {
        String url = properties.url();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private void esperar(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}